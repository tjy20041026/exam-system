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
 *
 * <h3>本类的整体结构</h3>
 * <pre>
 *   公开方法（接口实现）
 *     ├─ startExam / getPaper / saveAnswer / submit / getResult / myRecords
 *   私有辅助
 *     ├─ requireRecord  取记录（不存在就抛）
 *     ├─ assertOwner    归属校验
 *     ├─ buildPaperVO   组装试卷视图（getPaper 和 startExam 共用）
 *     ├─ loadQuestionMap / loadSavedAnswers  批量取数，消灭 N+1
 *     └─ grade          判分
 * </pre>
 *
 * <h3>贯穿本类的一条原则：绝不信任客户端传来的东西</h3>
 * <p>
 * 客户端会传 {@code examRecordId} 和 {@code questionId}，这两个值都必须校验：
 * <ul>
 *   <li>{@code examRecordId} —— 必须属于当前登录用户。否则学生甲改个数字
 *       就能看学生乙的卷子、甚至替乙交卷</li>
 *   <li>{@code questionId} —— 必须属于本场试卷。否则可以往考试记录里塞
 *       任意题目的答案，而判分时如果按"学生提交的题"去算分，就能凭空造分</li>
 * </ul>
 * 另一个更隐蔽的点：<b>分值一律从 paper_question 读，不接受客户端传分</b>。
 * 本类的判分逻辑里没有任何一处从请求里取值参与算分。
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
     * 作答态在 Redis 的存活时间 = 距离截止还剩多久 + 这个缓冲。
     * <p>
     * <b>为什么要留缓冲</b>：如果 TTL 严格等于考试时长，那么在考试
     * 最后一分钟答题的学生，会眼看着自己的答案 key 在交卷前一刻过期消失。
     * 留 30 分钟是为了让"考试结束"和"缓存过期"之间有个安全间隔，
     * 保证交卷时答案一定还在。
     * <p>
     * <b>为什么只留 30 分钟而不是几小时</b>：缓存的意义之一是自动回收。
     * 缓冲太长的话，异常退出的会话会在 Redis 里多占很久内存。
     * 30 分钟足够覆盖"交卷 + 兜底落库 + 人工排查"这几个环节了。
     */
    private static final long SESSION_TTL_BUFFER_SECONDS = 1800L;

    // ==================================================================
    //  开考
    // ==================================================================

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

        // 试卷自身的时间窗口 —— 这是"这场考试什么时候开放"，和
        // exam_record.deadline（"这个学生必须几点交卷"）是两个不同的概念。
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
            // 【并发兜底】两个请求同时开考时，都会走到上面那个"没查到"的分支，
            // 然后双双 INSERT。此时数据库的 uk_paper_user 唯一索引会把后到的那个拦下。
            //
            // 这里捕获异常后【重新查询并返回已有记录】，而不是报错 ——
            // 因为从用户视角看，"同时点了两下开考"不应该是一个错误，
            // 他想要的结果就是"进入考场"。返回已存在的记录正好满足这个诉求。
            //
            // 如果这里直接抛异常，学生看到的就是"系统繁忙"，
            // 但他其实已经开考了 —— 他会一直重试，一直失败，然后以为系统坏了。
            log.warn("并发开考被唯一索引拦截，改为返回已有记录: paperId={}, userId={}", paperId, userId);
            ExamRecord created = findRecord(paperId, userId);
            if (created == null) {
                // 理论上不可能走到这里：唯一索引冲突说明记录一定存在。
                // 但万一（比如事务隔离级别导致读不到），也得有个明确的错误，
                // 不能返回 null 让它在下游炸出一个 NullPointerException。
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
     * 确保 Redis 里有这个考试会话，并按"剩余时间 + 缓冲"设置过期。
     * <p>
     * 这个方法被设计成幂等的（底层用 HSETNX），所以重复开考、刷新页面
     * 都不会覆盖已有答案。
     * <p>
     * <b>注意 TTL 是在每次开考时重算的</b>，而不是只在第一次设。
     * 因为学生可能考到一半刷新页面，这时候重算一次能让 TTL
     * 跟着"当前剩余时间"走 —— 如果只在第一次设，一个 60 分钟的考试
     * 在开始时就定死了 90 分钟的 TTL，学生考满 60 分钟时，
     * 那个 key 只剩 30 分钟寿命，刚好够用但没有余量。
     * 每次重算之后，任何时刻的 TTL 都至少还有"剩余 + 30 分钟"。
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
        // 超时了但不允许继续答题，提示语用 EXAM_TIMEOUT 更准确 ——
        // 因为这时候学生看到的不是"我交过卷了"，而是"时间到了"。
        // 两个提示对用户的下一步动作指引完全不同：前者去查成绩，后者只能接受。
        if (record.isOverdue()) {
            throw new BizException(ResultCode.EXAM_TIMEOUT);
        }

        Paper paper = paperMapper.selectById(record.getPaperId());
        if (paper == null) {
            throw new BizException(ResultCode.PAPER_NOT_FOUND);
        }
        return buildPaperVO(record, paper);
    }

    // ==================================================================
    //  答题
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAnswer(Long examRecordId, SaveAnswerDTO dto) {
        ExamRecord record = requireRecord(examRecordId);
        assertOwner(record);

        if (record.getStatus().isFinished()) {
            throw new BizException(ResultCode.EXAM_ALREADY_SUBMITTED);
        }

        // 【关键】截止时间之后不再接受任何写入。
        //
        // 为什么这条判断必须在这里，而不能只靠前端倒计时：
        // 前端倒计时是可以绕过的 —— 学生打开开发者工具，
        // 在 Console 里直接调 fetch('/api/exam/xxx/answer', ...)，
        // 前端所有的"时间到了，禁止提交"逻辑一行都不会执行。
        // 服务端不拦，就等于没有时间限制。
        //
        // 顺带说一个容易忽略的点：这里用服务端的 LocalDateTime.now()，
        // 而不是任何来自请求的时间戳。客户端传的时间一律不可信。
        if (record.isOverdue()) {
            throw new BizException(ResultCode.EXAM_TIMEOUT);
        }

        // 校验题目确实属于本场试卷。
        // 少了这一步，学生可以给任意 questionId 写答案 ——
        // 虽然判分时不会被算分（判分是按 paper_question 遍历的），
        // 但这些脏数据会留在库里，让成绩单的统计和后续的学情分析全部失真。
        // 「不会被算分」不等于「可以写进来」。
        Long paperId = record.getPaperId();
        Long count = paperQuestionMapper.selectCount(
                Wrappers.<PaperQuestion>lambdaQuery()
                        .eq(PaperQuestion::getPaperId, paperId)
                        .eq(PaperQuestion::getQuestionId, dto.getQuestionId()));
        if (count == null || count == 0) {
            throw new BizException(ResultCode.QUESTION_NOT_IN_PAPER);
        }

        // ---------- 写入作答态 ----------
        //
        // Day 6 的核心改动：答案先写 Redis，而不是直接写 MySQL。
        //
        // 【为什么这样更快】
        // 改造前：每次答题 = 一条 UPDATE 语句。走网络到 MySQL、
        // 解析 SQL、定位行、加锁、写 redo log、等事务提交。
        // 一个班 50 人同时考试，每人每分钟答 2 题，就是 100 次写库/分钟，
        // 而这只是"答题"这一个动作产生的负载，还没算心跳和查询。
        //
        // 改造后：一次 HSET，纯内存操作，O(1)。
        //
        // 【代价是什么】
        // Redis 是内存数据库，断电/重启就没了。所以必须配
        //   · 定时兜底落库（把 Redis 内容定期同步到 MySQL）
        //   · 交卷时强制落库
        // 这两条保证最坏情况下只丢"最后一次同步之后的几道题"。
        boolean written = sessionService.saveAnswer(examRecordId, dto.getQuestionId(), dto.getUserAnswer());

        if (!written) {
            // 【降级路径】Redis 不可用 —— 直接写数据库。
            //
            // 这一条至关重要：没有它，Redis 一挂，学生就答不了题了。
            // 有它，Redis 挂了只是"退化回 Day 5 的版本" —— 慢一点，但能用。
            //
            // 这就是"缓存应该是可选的"这个原则的具体体现：
            //    加缓存是为了更快，不是为了更不可用。
            // 如果引入一个新组件反而让系统在它故障时完全不可用，
            // 那这个组件带来的不是收益，是风险。
            log.warn("Redis 不可用，作答降级为直写数据库: examRecordId={}, questionId={}",
                    examRecordId, dto.getQuestionId());
            saveAnswerToDatabase(examRecordId, dto.getQuestionId(), dto.getUserAnswer());
        }
    }

    /**
     * 把单题答案直接写进数据库（降级路径 + 交卷落库共用）。
     * <p>
     * 用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 实现幂等：
     * 靠 {@code uk_record_question(exam_record_id, question_id)} 唯一索引，
     * 同一题改十次库里也只有一行，内容是最后一次的值。
     * <p>
     * <b>为什么这个幂等性在这里是刚需</b>：
     * 同一条记录会被写很多次 —— 学生反复改答案、交卷时落库、
     * 定时任务兜底落库（可能和交卷的重叠）、并发下的重复请求。
     * 没有幂等性，这些路径每一条都会制造重复行，
     * 而重复的作答记录会让总分凭空多出来。
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

    // ==================================================================
    //  交卷 + 判分
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long examRecordId) {
        ExamRecord record = requireRecord(examRecordId);
        assertOwner(record);

        // ---------- 第 0 步：抢 Redis 交卷锁（性能优化，不是正确性保证）----------
        //
        // 关于"为什么有了数据库条件更新还要这层锁"、"为什么加了锁还要保留
        // 数据库那一层"，ExamSessionService#tryLockSubmit 的注释里有完整讨论。
        // 一句话概括：锁负责性能，数据库负责正确性。
        //
        // Redis 不可用时 tryLockSubmit 会返回一个假凭证（放行），
        // 所以这里不需要处理"拿不到锁是因为 Redis 挂了"的情况 ——
        // 那种情况下正确的做法就是放行，让数据库去挡。
        Optional<String> lockToken = sessionService.tryLockSubmit(examRecordId);
        if (lockToken.isEmpty()) {
            log.warn("交卷锁被占用，拒绝重复提交: examRecordId={}", examRecordId);
            // 提示语特意和"已交卷"区分开：这时候学生其实【还没交成功】，
            // 只是上一个请求正在处理。告诉他"已交卷"会让他以为可以去看成绩了，
            // 结果查成绩接口又会说"尚未交卷"，前后矛盾。
            throw new BizException(ResultCode.CONFLICT, "交卷正在处理中，请稍候，不要重复提交");
        }

        try {
            if (!doSubmit(record)) {
                throw new BizException(ResultCode.EXAM_ALREADY_SUBMITTED);
            }
        } finally {
            // 放在 finally 里，保证异常路径也能释放。
            // 不释放的话，剩下的 30 秒里这个学生都交不了卷 ——
            // 明明是自己的一次失败尝试，却把自己锁在门外半分钟。
            //
            // ⚠️ 一个值得注意的细节：因为本方法带 @Transactional，
            // 这个 finally 执行时【事务还没提交】—— 提交发生在方法返回之后。
            // 也就是说锁的释放和数据库状态的可见性之间有一个微小的错位窗口：
            // 锁已释放，但别的请求还看不到新的 status。
            //
            // 如果系统的正确性依赖这把锁，这里就是一个真实的漏洞。
            // 好在我们的正确性依赖的是数据库的条件更新 ——
            // 晚到的请求即使进了门，它的 UPDATE 也会在行锁上等到
            // 前一个事务提交，然后发现 status 已经不是 ONGOING，affected=0。
            //
            // 这个细节正好印证了那条原则：
            // 【不要把正确性建立在锁上，锁的时序你控制不住。】
            lockToken.ifPresent(t -> sessionService.unlockSubmit(examRecordId, t));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoSubmit(Long examRecordId) {
        // 不做归属校验 —— 定时任务没有"当前登录用户"，它就是替所有超时的人交卷。
        // 安全性由"本方法不可从 HTTP 到达" + "调用方的筛选条件足够严格"保证，
        // 详见 ExamService#autoSubmit 的接口注释。
        ExamRecord record = requireRecord(examRecordId);

        try {
            if (!doSubmit(record)) {
                // 正常情况下不会走到这里：能被 findOverdue 查出来的记录，
                // 状态就是 ONGOING，不可能已经被交过卷。
                // 但有两个真实场景会造成这个结果：
                //   ① 定时任务扫描后、处理前，学生自己点了交卷
                //   ② 上一轮定时任务处理到一半失败了，这一轮又捞到同一条
                // 两种都属于正常现象，记 info 就行 ——
                // 如果打成 error，日志里会充满这种"其实没事"的记录，
                // 真正需要关注的错误反而被淹没。
                log.info("自动交卷跳过（已被处理）: examRecordId={}", examRecordId);
            }
        } catch (BizException e) {
            // 单条失败不能影响整批。定时任务会继续处理下一条 ——
            // 这就是 ExamScheduler 里"逐条隔离异常"的由来。
            log.warn("自动交卷失败，跳过该条: examRecordId={}, 原因={}", examRecordId, e.getMessage());
        }
    }

    /**
     * 交卷的实际逻辑：抢数据库的锁 → 判分 → 写回分数。
     *
     * @return {@code true} 表示本请求抢到了交卷权并已完成判分；
     *         {@code false} 表示该记录已经被别人交过卷了
     */
    private boolean doSubmit(ExamRecord record) {
        Long examRecordId = record.getId();

        LocalDateTime now = LocalDateTime.now();

        // 超时交卷用 TIMEOUT 标记，正常交卷用 SUBMITTED。
        // 两者后续都要走判分流程，区别只在状态值，所以下面共用一个方法。
        ExamStatus claimStatus = record.isOverdue() ? ExamStatus.TIMEOUT : ExamStatus.SUBMITTED;

        int durationUsed = (int) Duration.between(record.getStartTime(), now).getSeconds();

        // ---------- 第 1 步：抢锁，这一步决定成败 ----------
        //
        // 注意这里判分【还没有开始】。必须先把状态从 ONGOING 改掉再判分，
        // 顺序反了的话，两个并发请求会各判一遍卷 —— 浪费资源是小事，
        // 真正的风险是它们可能得出不同结果（比如碰到边界情况），
        // 然后后写的那个覆盖先写的，最终分数取决于谁后提交，这不可接受。
        int affected = examRecordMapper.finishExam(examRecordId, claimStatus, now, durationUsed);
        if (affected != 1) {
            // affected == 0 只有两种可能：
            //   ① 记录不存在（但上面 requireRecord 已经查到了，所以排除）
            //   ② 状态已经不是 ONGOING —— 说明别的请求已经交了卷
            //
            // 这里是本系统防重复交卷的【唯一】判定点。
            // 因为判断和修改被写在同一条 UPDATE 里，靠 InnoDB 的行锁串行化，
            // 所以不会出现"两个请求都认为自己抢到了"的情况。
            //
            // 【为什么这里是 return false 而不是抛异常】
            // 因为这个方法有两个调用方，对"没抢到"的处理方式不同：
            //   · submit()     —— 学生的请求，要告诉用户"已交卷"
            //   · autoSubmit() —— 定时任务，这属于正常情况，记个 info 继续跑
            // 把"抛异常还是返回 false"的决定权交给调用方，
            // 而不是在这里替它们选一个。
            log.warn("重复交卷被拦截: examRecordId={}", examRecordId);
            return false;
        }

        log.info("交卷抢锁成功: examRecordId={}, claimStatus={}, durationUsed={}s",
                examRecordId, claimStatus, durationUsed);

        // ---------- 第 2 步：判分 ----------
        //
        // 能执行到这里，说明本请求是唯一抢到锁的那个，可以放心判分。
        // 此时即使 Redis 里的 session 还在、即使有别的地方在写答案，
        // 都不会影响结果 —— 因为对手已经在第 1 步被挡住了。
        GradeResult result = grade(record.getPaperId(), examRecordId);

        // ---------- 第 3 步：写回分数和最终状态 ----------
        //
        // 全部是客观题 → 直接 GRADED，成绩当场确定。
        // 有简答题 → 保持 claimStatus（SUBMITTED 或 TIMEOUT），等教师阅卷后转 GRADED。
        //
        // 为什么要区分？因为学生交完卷立刻就要看到分数。
        // 如果一律停在 SUBMITTED，一个纯选择题的考试也得等老师点一下"批阅"
        // 才能出分 —— 老师什么都没做，却卡住了学生的成绩。
        ExamStatus finalStatus = result.hasEssay ? claimStatus : ExamStatus.GRADED;
        examRecordMapper.updateScore(examRecordId, result.totalScore, finalStatus);

        // ---------- 第 4 步：清理 Redis 作答态 ----------
        //
        // 答案已经判分并落库了，Redis 里这份没有用了。
        // 主动删除而不是等 TTL 到期（最长可能还有 90 分钟），
        // 是为了尽快把内存还回去 —— 考试高峰期同时进行的考试可能很多，
        // 这些"用完了但还没过期"的数据会白白挤占内存，
        // 严重时触发淘汰策略，把别人正在用的作答态给挤掉。
        sessionService.clearSession(examRecordId);

        log.info("交卷完成: examRecordId={}, score={}/{}, finalStatus={}, 待阅卷简答题数={}",
                examRecordId, result.totalScore, result.fullScore, finalStatus, result.essayCount);
        return true;
    }

    // ==================================================================
    //  查成绩
    // ==================================================================

    @Override
    public ExamResultVO getResult(Long examRecordId) {
        ExamRecord record = requireRecord(examRecordId);
        assertOwner(record);

        // 没交卷就没有成绩。这里返回错误而不是一个空成绩单 ——
        // 因为"还没考完"和"考了 0 分"是完全不同的状态，
        // 用同一个响应表示会让前端没法区分，最终一定有人把 0 分显示给还没交卷的学生。
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
                // 题目被删了但试卷还引用着。跳过比抛异常好 ——
                // 学生查成绩时不该因为一条脏数据就看不到整张成绩单。
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
                // 简答题：correct 保持 null，前端据此显示"待阅卷"。
                // 注意这里【不能】把 correct 设成 false —— 那是在告诉学生
                // "你这题答错了"，而实际上老师根本还没看。谎报一个"错"，
                // 比诚实地显示"未知"糟糕得多。
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

        // 【消灭 N+1】先把所有 paperId 收集起来一次性查出来，
        // 而不是在下面的循环里对每条记录各查一次 paper。
        //
        // 10 场考试 = 1 次查 exam_record + 1 次查 paper = 2 次查询；
        // 换成循环里查就是 1 + 10 = 11 次。
        // 列表页是最容易被 N+1 拖垮的地方，因为它的数据量随用户使用自然增长 ——
        // 测试时只有 2 条记录，看不出任何问题；上线三个月后某个学生的列表
        // 要发 50 次查询，慢得莫名其妙。
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

    // ==================================================================
    //  定时任务专用
    // ==================================================================

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
                // Redis 不可用。这时候【整轮都别再试了】——
                // 熔断器已经打开，后面每一条都会立刻返回 empty，
                // 继续循环纯粹是浪费 CPU。直接结束这一轮。
                //
                // 注意这里【不抛异常】：兜底落库失败不是紧急故障，
                // 因为答案本身还在 Redis 里（只是没法同步）。
                // 真正危险的是 Redis 挂了【同时】数据库也没数据，
                // 那才会丢答案 —— 而这一轮不写库，恰恰不会破坏
                // 数据库里已有的那份旧快照。
                log.warn("兜底落库中断：Redis 不可用，本轮已处理 {} 场", flushed);
                break;
            }

            Map<Long, String> map = answers.get();
            if (map.isEmpty()) {
                // Redis 正常，但这场考试确实一题都没答。没什么可落库的。
                continue;
            }

            // 逐场独立提交，不放在一个大事务里。
            //
            // 如果整批一个事务，某一场因为脏数据失败会导致
            // 【前面已经成功的全部回滚】—— 一次失败抹掉一批成果。
            // 逐条提交时，失败的那条只影响它自己，下一轮定时任务还会重试它。
            //
            // 这也顺便让每次数据库交互都很短，不会长时间占着连接。
            try {
                List<AnswerRecord> list = new ArrayList<>(map.size());
                LocalDateTime now = LocalDateTime.now();
                for (Map.Entry<Long, String> e : map.entrySet()) {
                    AnswerRecord ar = new AnswerRecord();
                    ar.setExamRecordId(record.getId());
                    ar.setQuestionId(e.getKey());
                    ar.setUserAnswer(e.getValue());
                    // 考试进行中还没判分，这两列保持"未判定"状态。
                    //
                    // ⚠️ 这里有个必须想清楚的点：upsertBatch 的
                    // ON DUPLICATE KEY UPDATE 会把 is_correct 和 score
                    // 一并覆盖成这里的值（null 和 0）。
                    //
                    // 对本场景是安全的 —— 因为能被本方法处理的是
                    // status='ONGOING' 的记录，这类记录的这两列
                    // 本来就该是 null/0，不存在"覆盖掉已判分结果"的问题。
                    //
                    // 但这提醒了一件事：复用同一个 upsert 方法时，
                    // 【必须确认它更新的每一列在新场景下都是对的】。
                    // 直接拿来用而不检查更新列，是很容易埋雷的地方。
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

    // ==================================================================
    //  判分
    // ==================================================================

    /**
     * 判分结果。用私有内部类而不是散着返回几个值 ——
     * 判分要同时产出「总分」「是否还有简答题待阅卷」「简答题数量」三样东西，
     * 用返回值硬塞只能塞一个。
     */
    private static class GradeResult {
        int totalScore = 0;
        int fullScore = 0;
        boolean hasEssay = false;
        int essayCount = 0;
    }

    /**
     * 对本场考试的所有题目判分，并把结果写入 answer_record。
     *
     * <h3>为什么遍历的是 paper_question，而不是学生提交的答案</h3>
     * <p>
     * 因为这个方向决定了"没作答的题"怎么处理。
     * <p>
     * 按 {@code paper_question} 遍历，试卷上每道题都会被处理到 ——
     * 没作答的题自然拿到 0 分。而如果按学生提交的答案遍历，
     * 那些没作答的题就<b>根本不在循环里</b>，它们的分值会被静默地从总分里漏掉。
     * <p>
     * 后果是：一个交了白卷的学生，总分是 0（正确）；但如果满分 100 里
     * 他答了 3 道共 15 分的题、全对，按后一种写法他的得分率会是 100/100 ——
     * 因为分母也跟着只剩 15 了。<b>分子分母一起错，比率看起来完全正常。</b>
     * 这种 bug 在"大家都答完了"的测试数据下永远不会暴露。
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
            // 判分结果统一批量写回。这里【必须】用 upsert 而不是 insert ——
            // 因为答题过程中 saveAnswer 可能已经写过这些行了（学生答过的题），
            // 用 insert 会直接撞唯一索引报错。
            answerRecordMapper.upsertBatch(toSave);
        }
        return result;
    }

    /**
     * 判断一道客观题的作答是否正确。
     *
     * <h3>为什么不能直接 equals</h3>
     * <p>
     * 因为学生答案来自客户端，格式不受控。同样是"选 A 和 C"这道多选题，
     * 客户端可能传上来 {@code "A,C"}、{@code "a,c"}、
     * {@code "C,A"}、{@code "A, C"}（逗号后带空格）——
     * 在语义上它们全都对，但字符串比较全部不等。
     * <p>
     * 所以比较之前必须<b>归一化</b>：统一大小写、去掉多余空格、
     * 多选题额外把顺序拉平（{@code "C,A"} 和 {@code "A,C"} 是同一个答案）。
     *
     * <h3>多选题的给分策略：全对才得分</h3>
     * <p>
     * 很多考试系统支持"漏选给一半分"。这里<b>刻意不实现</b>，理由是：
     * 部分分的规则在不同考试里差异极大（漏选给一半？错选倒扣？），
     * 而且一旦学生的得分是"半对"状态，成绩单、总分、排名全都要跟着处理小数，
     * 每个下游环节都有出错的机会。
     * <p>
     * 更重要的是，这个规则应该由<b>业务方</b>决定，不该由实现者顺手定。
     * 与其实现一个"看起来合理但没跟任何人确认过"的规则，
     * 不如先用最不含糊的策略（全对才给分），把选择权留给需求方。
     * <p>
     * 如果将来要加，改动点只有这一个方法 —— 因为它被设计成了唯一的判定入口。
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

    /**
     * 把多选题答案拆成单词后排序再拼回去。
     * <p>
     * 排序是关键：{@code "C,A"} 和 {@code "A,C"} 排序后都变成 {@code "A,C"}。
     * 不排序的话这两个语义相同的答案会被判成不同。
     */
    private static String sortTokens(String s) {
        return java.util.Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .sorted()
                .collect(Collectors.joining(","));
    }

    // ==================================================================
    //  私有辅助
    // ==================================================================

    /**
     * 计算交卷截止时刻，取「开考时间 + 考试时长」和「试卷截止时间」中较早的那个。
     *
     * <h3>为什么要取较早的</h3>
     * <p>
     * 假设试卷时长 60 分钟，但试卷本身 10:00 就关闭了。
     * 学生 9:50 才开考 ——
     * <ul>
     *   <li>只算 {@code 9:50 + 60min = 10:50}：他 10:00 之后还在答题，
     *       而这场考试对外已经结束了，<b>别人能看到他还在考，甚至能通过他泄露题目</b></li>
     *   <li>取较早的 {@code min(10:50, 10:00) = 10:00}：他实际只考了 10 分钟。
     *       这对学生是"亏"的，但考试本身是公平的</li>
     * </ul>
     * 两个选项都不完美，但后者的问题（学生少考了）是可以通过
     * 「考前提示剩余时间」来规避的，而前者的问题（考试边界失效）没法补救。
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
     * 已有记录时决定返回什么。
     * <p>
     * 已交卷的返回一个明确的错误，而不是把试卷再发一遍 ——
     * 因为学生如果还能拿到题目，会以为自己"重新开考"了、时间也重置了，
     * 然后开始答题，最后交卷时才发现交不上去。<b>给他一个立刻能理解的错误，
     * 比给他一个看起来能用、实际处处碰壁的界面要好。</b>
     */
    private ExamPaperVO resume(ExamRecord existing, Paper paper) {
        if (existing.getStatus().isFinished()) {
            throw new BizException(ResultCode.EXAM_ALREADY_SUBMITTED);
        }
        if (existing.isOverdue()) {
            // 超时了但定时任务还没扫到（Day 6 才会做自动交卷）。
            // 这里不能放他进去答题 —— 否则会出现"超时 20 分钟后还在写答案"的漏洞，
            // 因为答题的时限判断是以 deadline 为准的，不受定时任务调度频率影响。
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
            // ⚠️ 这里【没有】v.setAnswer(...)。ExamQuestionVO 上根本没有这个字段。
            // 详见 ExamQuestionVO 的类注释。
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
     * 批量取题目实体，返回 id -> Question 的映射。
     * <p>
     * 先判空是必须的：{@code selectBatchIds} 传空集合会生成
     * {@code WHERE id IN ()} 这种语法错误的 SQL，直接抛异常。
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
     * 取本场考试已作答的内容，返回 questionId -> 答案。
     *
     * <h3>读取顺序：Redis 优先，MySQL 兜底</h3>
     * <p>
     * 这正是<b>断点续考</b>的实现核心。学生在另一台电脑登录、
     * 或者关掉浏览器几小时后再回来，调取卷接口就能拿回这些内容 ——
     * 因为作答状态存在<b>服务端</b>，不在浏览器里。
     * <p>
     * 存在服务端而不是 localStorage，带来两个前端存储给不了的能力：
     * <ol>
     *   <li><b>换设备能续考</b> —— 浏览器存储是跟设备走的，
     *       换台电脑就没了。服务端存储跟人走</li>
     *   <li><b>学生改不了</b> —— localStorage 里的内容，学生打开开发者工具
     *       想改就改。而这里的答案学生碰不到，改答案只能通过接口，
     *       接口又会校验归属和截止时间</li>
     * </ol>
     *
     * <h3>为什么最后还要合并 MySQL 的数据</h3>
     * <p>
     * 因为 Redis 里的内容可能不完整，有几种真实情况：
     * <ul>
     *   <li><b>考试进行到一半 Redis 挂了</b>，那段时间的答案是走降级路径
     *       直接写进 MySQL 的 —— 这时候 Redis 里根本没有这几道题</li>
     *   <li><b>Redis 出现机器级故障</b>（断电、磁盘损坏、AOF 文件损坏），
     *       数据没能完整恢复。本项目开了 AOF（appendfsync everysec），
     *       所以"进程重启"其实【不会】丢数据 —— 但 everysec 意味着
     *       最坏情况仍可能丢掉最后 1 秒的写入，机器级故障则可能丢得更多</li>
     *   <li><b>key 被主动删除或过了 TTL</b>。注意本项目实例配的是
     *       {@code maxmemory-policy noeviction} —— 内存满了会让写操作
     *       直接失败，而不是淘汰旧 key。<b>这是刻意的选择</b>：
     *       宁可写失败（然后走去数据库的降级路径），
     *       也不能静默地扔掉某个考生已经答完的题</li>
     * </ul>
     * 这几种情况下 Redis 要么是空的、要么不全，而 MySQL 里有兜底落库的数据。
     * <b>只读 Redis 的话，学生一刷新就会发现答案少了</b> ——
     * 这是最让人恐慌的一类故障，因为看起来像是"系统把我的答案弄丢了"。
     * <p>
     * 合并策略是<b>以 Redis 为准</b>（{@code putAll} 放在后面覆盖）：
     * Redis 是考试进行中的最新状态，MySQL 是定期快照，
     * 所以同一条数据两边都有时，Redis 的更新。
     *
     * <h3>用 LinkedHashMap 保持顺序</h3>
     * <p>
     * 这个 Map 最终会序列化成 JSON 返回。保持插入顺序能让
     * 多次调用的响应体在肉眼对比时保持一致 ——
     * 顺序乱的 HashMap 会让 diff 满屏噪音，排查问题时反而看不清真正的变化。
     */
    private Map<Long, String> loadSavedAnswers(Long examRecordId) {
        Map<Long, String> map = new LinkedHashMap<>();

        // ---------- 1. 先读数据库（兜底数据，可能较旧）----------
        //
        // 注意：这里【先】读库，是为了让 Redis 的数据能覆盖它。
        // 顺序反过来的话就要写一堆 if 判断谁更新，代码更复杂且容易错。
        for (AnswerRecord ar : listAnswersFromDatabase(examRecordId)) {
            // 只放真正答过的题。userAnswer 为 null 的记录不该出现在
            // "已作答内容"里 —— 那会让前端的"已答 N 题"统计把没碰过的题也算进去。
            // （判分时会为每道题都写一行记录，未作答的那些 user_answer 是 null）
            if (ar.getUserAnswer() != null) {
                map.put(ar.getQuestionId(), ar.getUserAnswer());
            }
        }

        // ---------- 2. 再读 Redis（最新状态，覆盖上面的）----------
        sessionService.getAnswers(examRecordId).ifPresent(map::putAll);
        //                                  ↑
        //  Optional.ifPresent：只有 Redis 正常返回时才合并。
        //  如果返回的是 Optional.empty()（Redis 不可用），
        //  这里什么都不做，直接用数据库那份 —— 降级完成。

        return map;
    }

    /** 从数据库读作答明细。抽出来是因为交卷判分和兜底落库都要用 */
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
     * 校验这场考试属于当前登录用户。
     * <p>
     * 少了这一步，学生只要把 URL 里的 examRecordId 改成别的数字，
     * 就能看到别人的卷子、替别人答题、甚至替别人交卷。
     * <p>
     * 这是最典型的一类越权漏洞 —— <b>接口做了登录校验，却没做归属校验</b>。
     * 登录校验只回答了"你是谁"，没回答"这条数据是不是你的"。
     * 很多系统栽在这里，因为测试时大家都只用自己账号，
     * 没人会去改 URL 里的 ID。
     * <p>
     * 用户 ID 必须从 {@code UserContext}（登录态）取，
     * <b>绝不能用请求参数里的 userId</b> —— 那样攻击者连改 URL 都省了。
     */
    private void assertOwner(ExamRecord record) {
        Long currentUserId = UserContext.getUserId();
        if (!record.getUserId().equals(currentUserId)) {
            log.warn("越权访问被拦截: examRecordId={}, ownerId={}, 请求者={}",
                    record.getId(), record.getUserId(), currentUserId);
            throw new BizException(ResultCode.EXAM_NOT_OWNED);
        }
    }

    /**
     * 得分率。保留一位小数。
     * <p>
     * 两个边界都要处理：总分为 null 或 0 时不能做除法（会得到 NaN 或抛异常），
     * 未判卷（score 为 null）时也不该算出一个假的 0%。
     */
    private static Double calcRate(Integer score, Integer totalScore) {
        if (score == null || totalScore == null || totalScore == 0) {
            return null;
        }
        return Math.round(score * 1000.0 / totalScore) / 10.0;
    }
}
