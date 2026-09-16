package com.exam.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.common.ResultCode;
import com.exam.common.UserContext;
import com.exam.common.exception.BizException;
import com.exam.dto.SaveAnswerDTO;
import com.exam.entity.AnswerRecord;
import com.exam.entity.ExamRecord;
import com.exam.entity.Paper;
import com.exam.entity.PaperQuestion;
import com.exam.entity.Question;
import com.exam.enums.ExamStatus;
import com.exam.enums.PaperStatus;
import com.exam.enums.QuestionType;
import com.exam.mapper.AnswerRecordMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.mapper.PaperMapper;
import com.exam.mapper.PaperQuestionMapper;
import com.exam.mapper.QuestionMapper;
import com.exam.service.ExamService;
import com.exam.service.ExamSessionService;
import com.exam.vo.ExamPaperVO;
import com.exam.vo.ExamQuestionVO;
import com.exam.vo.ExamRecordVO;
import com.exam.vo.ExamResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * 考试主流程实现。
 * <p>
 * 客户端传来的 examRecordId 和 questionId 都要校验：前者必须属于当前登录用户，
 * 后者必须属于本场试卷。分值一律从 paper_question 读，判分不从请求里取值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final ExamRecordMapper examRecordMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;

    /** 作答态的 Redis 存储。所有 Redis 访问都收口在它内部，本类不直接碰 RedisTemplate */
    private final ExamSessionService sessionService;

    /**
     * 作答态在 Redis 的存活时间 = 剩余时间 + 这个缓冲。TTL 严格等于考试时长的话，
     * 最后几分钟答的题会在交卷前过期；缓冲太长又会让异常退出的会话白占内存，
     * 30 分钟够覆盖交卷 + 兜底落库 + 排查这几个环节。
     */
    private static final long SESSION_TTL_BUFFER_SECONDS = 1800L;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamPaperVO startExam(Long paperId) {
        Long userId = UserContext.getUserId();

        Paper paper = paperMapper.selectById(paperId);
        if (paper == null) {
            throw new BizException(ResultCode.PAPER_NOT_FOUND);
        }
        if (!paper.getStatus().isOpenForExam()) {
            throw new BizException(ResultCode.PAPER_NOT_PUBLISHED);
        }

        // 试卷的时间窗口是"这场考试什么时候开放"，和 record 的 deadline
        //（这个学生必须几点交卷）是两回事
        LocalDateTime now = LocalDateTime.now();
        if (paper.getStartTime() != null && now.isBefore(paper.getStartTime())) {
            throw new BizException(ResultCode.EXAM_NOT_STARTED);
        }
        if (paper.getEndTime() != null && now.isAfter(paper.getEndTime())) {
            throw new BizException(ResultCode.EXAM_ENDED);
        }

        // 先查有没有历史记录 —— 有就复用，这是"断点续考"的入口
        ExamRecord existing = findRecord(paperId, userId);
        if (existing != null) {
            ExamPaperVO vo = resume(existing, paper);   // 内部会先校验状态，不合法的直接抛
            ensureSession(existing);
            return vo;
        }

        // 没有就新建
        ExamRecord record = new ExamRecord();
        record.setPaperId(paperId);
        record.setUserId(userId);
        record.setStartTime(now);
        record.setDeadline(calcDeadline(paper, now));
        record.setStatus(ExamStatus.ONGOING);
        record.setScore(null);          // 明确置 null：0 分和"还没判"是两回事
        record.setSubmitTime(null);
        record.setDurationUsed(0);

        try {
            examRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发开考时两个请求都会走到上面那个"没查到"的分支然后双双 INSERT，
            // uk_paper_user 会拦下后到的那个。这里重新查询并返回已有记录而不是报错 ——
            // "同时点了两下开考"不该让学生看到"系统繁忙"，他其实已经开考了
            log.warn("并发开考被唯一索引拦截，改为返回已有记录: paperId={}, userId={}", paperId, userId);
            ExamRecord created = findRecord(paperId, userId);
            if (created == null) {
                // 唯一索引冲突说明记录一定存在，这里只是兜底，
                // 避免返回 null 在下游炸出 NullPointerException
                throw new BizException(ResultCode.SYSTEM_ERROR, "开考失败，请刷新后重试");
            }
            ExamPaperVO vo = resume(created, paper);
            ensureSession(created);
            return vo;
        }

        ensureSession(record);

        log.info("开考成功: userId={}, paperId={}, examRecordId={}, deadline={}",
                userId, paperId, record.getId(), record.getDeadline());
        return buildPaperVO(record, paper);
    }

    /**
     * 确保 Redis 里有这个考试会话，TTL = 剩余时间 + 缓冲。底层 HSETNX 幂等，
     * 重复开考不覆盖已有答案。TTL 每次开考都重算而不是只设一次 ——
     * 只在第一次设的话，60 分钟的考试开局就定死 90 分钟，学生考到最后 key 已没多少余量。
     */
    private void ensureSession(ExamRecord record) {
        long ttl = record.remainingSeconds() + SESSION_TTL_BUFFER_SECONDS;
        sessionService.initSession(record.getId(), ttl);
    }

    @Override
    public ExamPaperVO getPaper(Long examRecordId) {
        ExamRecord record = requireRecord(examRecordId);
        assertOwner(record);

        if (record.getStatus().isFinished()) {
            throw new BizException(ResultCode.EXAM_ALREADY_SUBMITTED);
        }
        // 超时用 EXAM_TIMEOUT 而不是"已交卷"：学生看到"我交过卷了"会去查成绩，
        // 但他其实还没交
        if (record.isOverdue()) {
            throw new BizException(ResultCode.EXAM_TIMEOUT);
        }

        Paper paper = paperMapper.selectById(record.getPaperId());
        if (paper == null) {
            throw new BizException(ResultCode.PAPER_NOT_FOUND);
        }
        return buildPaperVO(record, paper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAnswer(Long examRecordId, SaveAnswerDTO dto) {
        ExamRecord record = requireRecord(examRecordId);
        assertOwner(record);

        if (record.getStatus().isFinished()) {
            throw new BizException(ResultCode.EXAM_ALREADY_SUBMITTED);
        }

        // 截止时间之后不再接受任何写入。不能只靠前端倒计时 ——
        // 前端那套是可以绕过的，开开发者工具直接调接口，"禁止提交"的逻辑一行都不会执行。
        // 时间用服务端的 now()，客户端传的一律不可信
        if (record.isOverdue()) {
            throw new BizException(ResultCode.EXAM_TIMEOUT);
        }

        // 校验题目确实属于本场试卷。少了这步学生可以给任意 questionId 写答案 ——
        // 判分按 paper_question 遍历虽然不会算分，但脏数据会留在库里让统计失真，
        // "不会被算分"不等于"可以写进来"
        Long paperId = record.getPaperId();
        Long count = paperQuestionMapper.selectCount(
                Wrappers.<PaperQuestion>lambdaQuery()
                        .eq(PaperQuestion::getPaperId, paperId)
                        .eq(PaperQuestion::getQuestionId, dto.getQuestionId()));
        if (count == null || count == 0) {
            throw new BizException(ResultCode.QUESTION_NOT_IN_PAPER);
        }

        // 答案先写 Redis 而不是 MySQL：一次 HSET 是纯内存操作，
        // 比每条答案走一遍 UPDATE（网络、加锁、redo log、等提交）快得多。
        // 代价是 Redis 不持久，得靠定时兜底落库和交卷时强制落库顶住
        boolean written = sessionService.saveAnswer(examRecordId, dto.getQuestionId(), dto.getUserAnswer());

        if (!written) {
            // 降级路径：Redis 不可用就直写数据库。没有这一条，Redis 一挂学生就答不了题了 ——
            // 加缓存是为了更快，不是为了让它故障时系统直接不可用
            log.warn("Redis 不可用，作答降级为直写数据库: examRecordId={}, questionId={}",
                    examRecordId, dto.getQuestionId());
            saveAnswerToDatabase(examRecordId, dto.getQuestionId(), dto.getUserAnswer());
        }
    }

    /**
     * 把单题答案直接写进数据库（降级路径和交卷落库共用）。靠
     * {@code INSERT ... ON DUPLICATE KEY UPDATE} 和 uk_record_question 唯一索引幂等：
     * 学生反复改答案、交卷落库、定时兜底都可能写同一条，不幂等就会出重复行，总分凭空多出来。
     */
    private void saveAnswerToDatabase(Long examRecordId, Long questionId, String userAnswer) {
        AnswerRecord answer = new AnswerRecord();
        answer.setExamRecordId(examRecordId);
        answer.setQuestionId(questionId);
        answer.setUserAnswer(userAnswer);
        answer.setIsCorrect(null);   // 判分在交卷时统一进行，答题过程中不判
        answer.setScore(0);
        answer.setSubmitTime(LocalDateTime.now());
        upsertOne(answer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long examRecordId) {
        ExamRecord record = requireRecord(examRecordId);
        assertOwner(record);

        // 先抢 Redis 交卷锁。它只削峰，正确性靠数据库的条件更新兜底，
        // 取舍见 ExamSessionService#tryLockSubmit。Redis 挂了会返回假凭证放行
        Optional<String> lockToken = sessionService.tryLockSubmit(examRecordId);
        if (lockToken.isEmpty()) {
            log.warn("交卷锁被占用，拒绝重复提交: examRecordId={}", examRecordId);
            // 提示语和"已交卷"区分开：这时候学生还没交成功，只是上一个请求在处理，
            // 说成"已交卷"他会去看成绩，而查成绩接口又说他尚未交卷
            throw new BizException(ResultCode.CONFLICT, "交卷正在处理中，请稍候，不要重复提交");
        }

        try {
            if (!doSubmit(record)) {
                throw new BizException(ResultCode.EXAM_ALREADY_SUBMITTED);
            }
        } finally {
            // 放 finally 里，保证异常路径也释放，否则这个学生剩下的 30 秒都交不了卷。
            // 注意本方法带 @Transactional，finally 执行时事务还没提交 —— 好在正确性靠的是
            // 数据库条件更新：晚到的请求会卡在行锁上，等到提交后发现 affected=0
            lockToken.ifPresent(t -> sessionService.unlockSubmit(examRecordId, t));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoSubmit(Long examRecordId) {
        // 不做归属校验：定时任务没有登录用户，安全性由"不可从 HTTP 到达"
        // 和调用方的筛选条件保证，详见 ExamService#autoSubmit
        ExamRecord record = requireRecord(examRecordId);

        try {
            if (!doSubmit(record)) {
                // 正常走不到：能被 findOverdue 查出来的都是 ONGOING。但学生自己
                // 点了交卷、或上一轮定时任务处理到一半失败，都会让这轮又捞到同一条，
                // 属于正常现象，记 info 就行 —— 打成 error 会把这行日志淹掉
                log.info("自动交卷跳过（已被处理）: examRecordId={}", examRecordId);
            }
        } catch (BizException e) {
            // 逐条隔离异常：单条失败不能影响这一批剩下的记录
            log.warn("自动交卷失败，跳过该条: examRecordId={}, 原因={}", examRecordId, e.getMessage());
        }
    }

    /**
     * 交卷的实际逻辑：抢数据库的锁 → 判分 → 写回分数。
     *
     * @return {@code true} 表示本请求抢到了交卷权并判完卷；
     *         {@code false} 表示这条记录已经被别人交过卷了
     */
    private boolean doSubmit(ExamRecord record) {
        Long examRecordId = record.getId();

        LocalDateTime now = LocalDateTime.now();

        // 超时交卷标 TIMEOUT，正常交卷标 SUBMITTED，后续判分流程一样
        ExamStatus claimStatus = record.isOverdue() ? ExamStatus.TIMEOUT : ExamStatus.SUBMITTED;

        int durationUsed = (int) Duration.between(record.getStartTime(), now).getSeconds();

        // 先把状态从 ONGOING 改掉再判分。顺序反了的话两个并发请求会各判一遍卷，
        // 碰到边界情况可能得出不同结果，最终分数取决于谁后写
        int affected = examRecordMapper.finishExam(examRecordId, claimStatus, now, durationUsed);
        if (affected != 1) {
            // affected=0 说明状态已经不是 ONGOING，别的请求交了卷。判断和修改写在同一条
            // UPDATE 里靠 InnoDB 行锁串行化，这里是防重复交卷的唯一判定点。返回 false 而不是
            // 抛异常，是因为 submit() 要报"已交卷"、autoSubmit() 只需记日志，决定权交给调用方
            log.warn("重复交卷被拦截: examRecordId={}", examRecordId);
            return false;
        }

        log.info("交卷抢锁成功: examRecordId={}, claimStatus={}, durationUsed={}s",
                examRecordId, claimStatus, durationUsed);

        // 走到这里说明本请求是唯一抢到锁的那个，可以放心判分
        GradeResult result = grade(record.getPaperId(), examRecordId);

        // 全是客观题直接 GRADED，有简答题则停在 claimStatus 等教师阅卷。
        // 学生交完卷就要看到分数，纯选择题不该等老师点一下"批阅"才出分
        ExamStatus finalStatus = result.hasEssay ? claimStatus : ExamStatus.GRADED;
        examRecordMapper.updateScore(examRecordId, result.totalScore, finalStatus);

        // 答案已判分落库，主动删掉 Redis 里这份而不是等 TTL 到期：
        // 高峰期"用完了但没过期"的数据会白占内存，挤掉别人正在用的作答态
        sessionService.clearSession(examRecordId);

        log.info("交卷完成: examRecordId={}, score={}/{}, finalStatus={}, 待阅卷简答题数={}",
                examRecordId, result.totalScore, result.fullScore, finalStatus, result.essayCount);
        return true;
    }

    @Override
    public ExamResultVO getResult(Long examRecordId) {
        ExamRecord record = requireRecord(examRecordId);
        assertOwner(record);

        // 返回错误而不是空成绩单："还没考完"和"考了 0 分"是两个状态，
        // 用同一个响应表示，前端迟早会把 0 分显示给还没交卷的学生
        if (record.getStatus() == ExamStatus.ONGOING) {
            throw new BizException(ResultCode.EXAM_NOT_SUBMITTED);
        }

        Paper paper = paperMapper.selectById(record.getPaperId());
        List<PaperQuestion> paperQuestions = listPaperQuestions(record.getPaperId());
        Map<Long, Question> questionMap = loadQuestionMap(paperQuestions);
        Map<Long, String> savedAnswers = loadSavedAnswers(examRecordId);

        ExamResultVO vo = new ExamResultVO();
        vo.setExamRecordId(examRecordId);
        vo.setPaperId(record.getPaperId());
        vo.setPaperTitle(paper != null ? paper.getTitle() : null);
        vo.setStatus(record.getStatus().name());
        vo.setStatusLabel(record.getStatus().getLabel());
        vo.setScore(record.getScore());
        vo.setTotalScore(paper != null ? paper.getTotalScore() : null);
        vo.setStartTime(record.getStartTime());
        vo.setSubmitTime(record.getSubmitTime());
        vo.setDurationUsed(record.getDurationUsed());

        List<ExamResultVO.AnswerDetailVO> details = new ArrayList<>();
        boolean hasEssay = false;

        for (PaperQuestion pq : paperQuestions) {
            Question q = questionMap.get(pq.getQuestionId());
            if (q == null) {
                // 题目被删了但试卷还引用着。跳过比抛异常好，
                // 不该因为一条脏数据让学生看不到整张成绩单
                log.warn("成绩单引用了不存在的题目: questionId={}, examRecordId={}",
                        pq.getQuestionId(), examRecordId);
                continue;
            }

            ExamResultVO.AnswerDetailVO d = new ExamResultVO.AnswerDetailVO();
            d.setSortOrder(pq.getSortOrder());
            d.setQuestionId(pq.getQuestionId());
            d.setContent(q.getContent());
            d.setTypeLabel(q.getType().getLabel());
            d.setUserAnswer(savedAnswers.get(pq.getQuestionId()));
            d.setFullScore(pq.getScore());

            if (q.getType().isObjective()) {
                boolean correct = isCorrect(q.getType(), q.getAnswer(), savedAnswers.get(pq.getQuestionId()));
                d.setCorrect(correct);
                d.setScore(correct ? pq.getScore() : 0);
            } else {
                // 简答题 correct 保持 null，前端据此显示"待阅卷"。
                // 设成 false 等于告诉学生答错了，可老师根本还没看
                d.setCorrect(null);
                d.setScore(0);
                hasEssay = true;
            }
            details.add(d);
        }

        vo.setDetails(details);
        vo.setHasPendingEssay(hasEssay);
        vo.setScoreRate(calcRate(record.getScore(), paper != null ? paper.getTotalScore() : null));
        return vo;
    }

    @Override
    public List<ExamRecordVO> myRecords() {
        Long userId = UserContext.getUserId();

        List<ExamRecord> records = examRecordMapper.selectList(
                Wrappers.<ExamRecord>lambdaQuery()
                        .eq(ExamRecord::getUserId, userId)
                        .orderByDesc(ExamRecord::getStartTime));
        if (records.isEmpty()) {
            return List.of();
        }

        // 消灭 N+1：先把 paperId 收集起来一次查完，别在下面循环里逐条查 paper。
        // 列表页最容易被这个拖垮 —— 测试时只有两条记录看不出问题，
        // 用久了某次列表要发几十条 SQL
        List<Long> paperIds = records.stream()
                .map(ExamRecord::getPaperId)
                .distinct()
                .collect(toList());
        Map<Long, Paper> paperMap = paperMapper.selectBatchIds(paperIds).stream()
                .collect(Collectors.toMap(Paper::getId, Function.identity(), (a, b) -> a));

        return records.stream().map(r -> {
            Paper p = paperMap.get(r.getPaperId());
            ExamRecordVO vo = new ExamRecordVO();
            vo.setExamRecordId(r.getId());
            vo.setPaperId(r.getPaperId());
            vo.setPaperTitle(p != null ? p.getTitle() : null);
            vo.setStatus(r.getStatus().name());
            vo.setStatusLabel(r.getStatus().getLabel());
            vo.setScore(r.getScore());
            vo.setTotalScore(p != null ? p.getTotalScore() : null);
            vo.setStartTime(r.getStartTime());
            vo.setSubmitTime(r.getSubmitTime());
            vo.setDurationUsed(r.getDurationUsed());
            vo.setCanContinue(r.canAnswer());
            return vo;
        }).collect(toList());
    }

    @Override
    public List<Long> findOverdueExamRecordIds(int limit) {
        return examRecordMapper.findOverdue(LocalDateTime.now(), limit)
                .stream()
                .map(ExamRecord::getId)
                .collect(toList());
    }

    @Override
    public int flushOngoingSessions(int limit) {
        List<ExamRecord> ongoing = examRecordMapper.findOngoing(LocalDateTime.now(), limit);
        if (ongoing.isEmpty()) {
            return 0;
        }

        int flushed = 0;
        for (ExamRecord record : ongoing) {
            Optional<Map<Long, String>> answers = sessionService.getAnswers(record.getId());

            if (answers.isEmpty()) {
                // Redis 不可用就直接结束这一轮：熔断器已打开，后面每条都会立刻返回 empty，
                // 继续循环纯属浪费 CPU。这里不抛异常 —— 答案还在 Redis 里，只是没法同步，
                // 本轮不写库也不会破坏数据库里已有的旧快照
                log.warn("兜底落库中断：Redis 不可用，本轮已处理 {} 场", flushed);
                break;
            }

            Map<Long, String> map = answers.get();
            if (map.isEmpty()) {
                continue;
            }

            // 逐场独立提交，不放在一个大事务里：整批一个事务的话，
            // 某一场失败会把前面成功的全部回滚；逐条提交时失败只影响自己，
            // 下一轮定时任务还会重试它
            try {
                List<AnswerRecord> list = new ArrayList<>(map.size());
                LocalDateTime now = LocalDateTime.now();
                for (Map.Entry<Long, String> e : map.entrySet()) {
                    AnswerRecord ar = new AnswerRecord();
                    ar.setExamRecordId(record.getId());
                    ar.setQuestionId(e.getKey());
                    ar.setUserAnswer(e.getValue());
                    // 考试进行中还没判分，这两列保持"未判定"。
                    // upsertBatch 的 ON DUPLICATE KEY UPDATE 会把 is_correct 和 score
                    // 一并覆盖成这里传的 null / 0 —— 能进本方法的都是 ONGOING 的记录，
                    // 这两列本来就该是空，所以安全。复用 upsert 时要确认它更新的每一列都对
                    ar.setIsCorrect(null);
                    ar.setScore(0);
                    ar.setSubmitTime(now);
                    list.add(ar);
                }
                answerRecordMapper.upsertBatch(list);
                flushed++;
            } catch (Exception e) {
                // 单场失败不影响其余场次
                log.error("兜底落库失败，跳过该场: examRecordId={}", record.getId(), e);
            }
        }
        return flushed;
    }

    /** 判分结果：总分、满分、是否还有简答题待阅卷。判分要同时产出好几样东西，塞不进一个返回值 */
    private static class GradeResult {
        int totalScore = 0;
        int fullScore = 0;
        boolean hasEssay = false;
        int essayCount = 0;
    }

    /**
     * 对本场考试的所有题目判分，并把结果写入 answer_record。
     * <p>
     * 遍历 paper_question 而不是学生提交的答案：按试卷遍历每道题都会走到，
     * 没作答的自然拿 0 分；按提交的答案遍历，没作答的题根本不在循环里，
     * 分值会被静默漏掉 —— 分子分母一起错，得分率看起来完全正常。
     */
    private GradeResult grade(Long paperId, Long examRecordId) {
        List<PaperQuestion> paperQuestions = listPaperQuestions(paperId);
        Map<Long, Question> questionMap = loadQuestionMap(paperQuestions);
        Map<Long, String> savedAnswers = loadSavedAnswers(examRecordId);

        GradeResult result = new GradeResult();
        List<AnswerRecord> toSave = new ArrayList<>();

        for (PaperQuestion pq : paperQuestions) {
            result.fullScore += pq.getScore();

            Question q = questionMap.get(pq.getQuestionId());
            if (q == null) {
                continue;
            }

            AnswerRecord ar = new AnswerRecord();
            ar.setExamRecordId(examRecordId);
            ar.setQuestionId(pq.getQuestionId());
            ar.setUserAnswer(savedAnswers.get(pq.getQuestionId()));
            ar.setSubmitTime(LocalDateTime.now());

            if (q.getType().isObjective()) {
                boolean correct = isCorrect(q.getType(), q.getAnswer(), ar.getUserAnswer());
                ar.setIsCorrect(correct ? 1 : 0);
                ar.setScore(correct ? pq.getScore() : 0);
                result.totalScore += ar.getScore();
            } else {
                ar.setIsCorrect(null);   // 待人工阅卷
                ar.setScore(0);
                result.hasEssay = true;
                result.essayCount++;
            }
            toSave.add(ar);
        }

        if (!toSave.isEmpty()) {
            // 必须 upsert 而不是 insert：答题过程中 saveAnswer 可能已经写过这些行了
            answerRecordMapper.upsertBatch(toSave);
        }
        return result;
    }

    /**
     * 判断一道客观题的作答是否正确。不能直接 equals：答案来自客户端，格式不受控 ——
     * "A,C" / "a,c" / "C,A" / "A, C" 语义都对但字符串全不等，所以先归一化（统一大小写、
     * 去空格，多选题再把顺序拉平）。多选题全对才给分是刻意的：部分分规则各考试差异极大，
     * "半对"还会让总分、排名出现小数，该由业务方定，将来要加改动点也只有这个方法。
     */
    private static boolean isCorrect(QuestionType type, String standard, String userAnswer) {
        String std = normalize(userAnswer);
        String expected = normalize(standard);

        if (std.isEmpty() || expected.isEmpty()) {
            return false;
        }
        if (type == QuestionType.MULTIPLE) {
            return sortTokens(std).equals(sortTokens(expected));
        }
        return std.equals(expected);
    }

    /** 归一化：null 安全、去首尾空格、统一大写 */
    private static String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    /** 拆单词后排序再拼回去：不排序的话 "C,A" 和 "A,C" 会被判成不同答案 */
    private static String sortTokens(String s) {
        return java.util.Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .sorted()
                .collect(Collectors.joining(","));
    }

    /**
     * 计算交卷截止时刻，取「开考时间 + 考试时长」和「试卷截止时间」中较早的。
     * <p>
     * 只算时长的话，一场 10:00 就结束的考试，9:50 开考的学生会一直答到 10:50 ——
     * 考试对外已经结束了，别人还能看到他坐在考场里。取较早的他会少考一会儿，
     * 亏，但考试边界不会失效。
     */
    private static LocalDateTime calcDeadline(Paper paper, LocalDateTime startTime) {
        LocalDateTime byDuration = startTime.plusMinutes(paper.getDuration());
        LocalDateTime byPaperEnd = paper.getEndTime();

        if (byPaperEnd == null) {
            return byDuration;
        }
        return byDuration.isBefore(byPaperEnd) ? byDuration : byPaperEnd;
    }

    /** 按 (paperId, userId) 查考试记录。数据库层有唯一索引保证最多一条 */
    private ExamRecord findRecord(Long paperId, Long userId) {
        return examRecordMapper.selectOne(
                Wrappers.<ExamRecord>lambdaQuery()
                        .eq(ExamRecord::getPaperId, paperId)
                        .eq(ExamRecord::getUserId, userId));
    }

    /**
     * 已有记录时决定返回什么。已交卷的返回明确错误，而不是把试卷再发一遍 ——
     * 学生拿到题目会以为重新开考、时间也重置了，答完才发现交不上去。
     */
    private ExamPaperVO resume(ExamRecord existing, Paper paper) {
        if (existing.getStatus().isFinished()) {
            throw new BizException(ResultCode.EXAM_ALREADY_SUBMITTED);
        }
        if (existing.isOverdue()) {
            // 超时但定时任务还没扫到，也不能放进去答题：答题时限以 deadline 为准，
            // 不受定时任务调度频率影响
            throw new BizException(ResultCode.EXAM_TIMEOUT);
        }
        log.info("复用已有考试记录（断点续考）: examRecordId={}, 已答{}题",
                existing.getId(), countSaved(existing.getId()));
        return buildPaperVO(existing, paper);
    }

    private ExamPaperVO buildPaperVO(ExamRecord record, Paper paper) {
        List<PaperQuestion> paperQuestions = listPaperQuestions(record.getPaperId());
        Map<Long, Question> questionMap = loadQuestionMap(paperQuestions);

        List<ExamQuestionVO> questions = new ArrayList<>();
        for (PaperQuestion pq : paperQuestions) {
            Question q = questionMap.get(pq.getQuestionId());
            if (q == null) {
                continue;
            }
            ExamQuestionVO v = new ExamQuestionVO();
            v.setQuestionId(q.getId());
            v.setSortOrder(pq.getSortOrder());
            v.setContent(q.getContent());
            v.setType(q.getType());
            v.setTypeLabel(q.getType().getLabel());
            v.setOptions(q.getType().needsOptions() ? q.getOptions() : null);
            v.setScore(pq.getScore());
            // 这里不 setAnswer：ExamQuestionVO 上根本没这个字段，详见它的类注释
            questions.add(v);
        }
        questions.sort(Comparator.comparing(ExamQuestionVO::getSortOrder,
                Comparator.nullsLast(Comparator.naturalOrder())));

        ExamPaperVO vo = new ExamPaperVO();
        vo.setExamRecordId(record.getId());
        vo.setPaperId(record.getPaperId());
        vo.setPaperTitle(paper.getTitle());
        vo.setTotalScore(paper.getTotalScore());
        vo.setStartTime(record.getStartTime());
        vo.setDeadline(record.getDeadline());
        vo.setRemainingSeconds(record.remainingSeconds());
        vo.setStatus(record.getStatus().name());
        vo.setQuestions(questions);
        vo.setSavedAnswers(loadSavedAnswers(record.getId()));
        return vo;
    }

    /** 取试卷的题目关联，按题号升序 */
    private List<PaperQuestion> listPaperQuestions(Long paperId) {
        return paperQuestionMapper.selectList(
                Wrappers.<PaperQuestion>lambdaQuery()
                        .eq(PaperQuestion::getPaperId, paperId)
                        .orderByAsc(PaperQuestion::getSortOrder));
    }

    /**
     * 批量取题目，返回 id -> Question。先判空是必须的：
     * {@code selectBatchIds} 传空集合会生成 {@code WHERE id IN ()} 直接报错。
     */
    private Map<Long, Question> loadQuestionMap(List<PaperQuestion> paperQuestions) {
        if (paperQuestions.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = paperQuestions.stream()
                .map(PaperQuestion::getQuestionId)
                .distinct()
                .collect(toList());

        Map<Long, Question> map = new HashMap<>(ids.size());
        for (Question q : questionMapper.selectBatchIds(ids)) {
            map.put(q.getId(), q);
        }
        return map;
    }

    /**
     * 取本场考试已作答的内容，返回 questionId -> 答案。Redis 优先、MySQL 兜底。
     * <p>
     * 这是断点续考的实现核心：作答态存在服务端而不是 localStorage，所以换台电脑能接着考，
     * 学生也改不了自己的答案。必须合并数据库那份 —— 考试中途 Redis 挂过的话，
     * 那段时间的答案是走降级路径直接写库的；只读 Redis，学生一刷新就会发现答案少了。
     * 两边都有时以 Redis 为准，它是考试进行中的最新状态。
     */
    private Map<Long, String> loadSavedAnswers(Long examRecordId) {
        Map<Long, String> map = new LinkedHashMap<>();

        // 先读库（较旧的兜底数据），让后面的 Redis 覆盖它。反过来写就得判断谁更新，更绕
        for (AnswerRecord ar : listAnswersFromDatabase(examRecordId)) {
            // 只放真正答过的题。判分会给每道题都写一行，未作答的 user_answer 是 null，
            // 放进来会让前端的"已答 N 题"算多
            if (ar.getUserAnswer() != null) {
                map.put(ar.getQuestionId(), ar.getUserAnswer());
            }
        }

        // 再读 Redis（最新状态）覆盖上面的。Redis 不可用时 ifPresent 什么都不做，
        // 直接用数据库那份 —— 降级完成
        sessionService.getAnswers(examRecordId).ifPresent(map::putAll);

        return map;
    }

    /** 从数据库读作答明细。抽出来是因为取卷和查成绩都要用 */
    private List<AnswerRecord> listAnswersFromDatabase(Long examRecordId) {
        return answerRecordMapper.selectList(
                Wrappers.<AnswerRecord>lambdaQuery()
                        .eq(AnswerRecord::getExamRecordId, examRecordId));
    }

    private int countSaved(Long examRecordId) {
        Long c = answerRecordMapper.selectCount(
                Wrappers.<AnswerRecord>lambdaQuery()
                        .eq(AnswerRecord::getExamRecordId, examRecordId));
        return c == null ? 0 : c.intValue();
    }

    /** 单条 upsert。走的是同一个 ON DUPLICATE KEY UPDATE 语义 */
    private void upsertOne(AnswerRecord answer) {
        answerRecordMapper.upsertBatch(List.of(answer));
    }

    private ExamRecord requireRecord(Long examRecordId) {
        ExamRecord record = examRecordMapper.selectById(examRecordId);
        if (record == null) {
            throw new BizException(ResultCode.EXAM_RECORD_NOT_FOUND);
        }
        return record;
    }

    /**
     * 校验这场考试属于当前登录用户。少了这一步，URL 里的 examRecordId 改成别的数字
     * 就能看别人的卷子、替别人交卷 —— 登录校验只回答"是谁在请求"，
     * 不回答"这条数据是不是他的"。用户 ID 必须从登录态取，不能用请求参数里的 userId。
     */
    private void assertOwner(ExamRecord record) {
        Long currentUserId = UserContext.getUserId();
        if (!record.getUserId().equals(currentUserId)) {
            log.warn("越权访问被拦截: examRecordId={}, ownerId={}, 请求者={}",
                    record.getId(), record.getUserId(), currentUserId);
            throw new BizException(ResultCode.EXAM_NOT_OWNED);
        }
    }

    /** 得分率，保留一位小数。总分为 null 或 0 时不能做除法，未判卷时也不该算出假的 0% */
    private static Double calcRate(Integer score, Integer totalScore) {
        if (score == null || totalScore == null || totalScore == 0) {
            return null;
        }
        return Math.round(score * 1000.0 / totalScore) / 10.0;
    }
}
