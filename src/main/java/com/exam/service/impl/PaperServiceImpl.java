package com.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.PageResult;
import com.exam.common.ResultCode;
import com.exam.common.UserContext;
import com.exam.common.exception.BizException;
import com.exam.converter.PaperConverter;
import com.exam.dto.PaperComposeDTO;
import com.exam.dto.PaperCreateDTO;
import com.exam.dto.PaperQueryDTO;
import com.exam.dto.PaperQuestionCount;
import com.exam.entity.Paper;
import com.exam.entity.PaperQuestion;
import com.exam.entity.Question;
import com.exam.enums.PaperStatus;
import com.exam.mapper.PaperMapper;
import com.exam.mapper.PaperQuestionMapper;
import com.exam.mapper.QuestionMapper;
import com.exam.service.PaperService;
import com.exam.vo.PaperDetailVO;
import com.exam.vo.PaperVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 试卷服务实现。
 *
 * <h3>本类的核心：状态机 + 派生值维护</h3>
 * <p>
 * 两个贯穿始终的约束：
 * <ol>
 *   <li><b>已发布的试卷不能改</b>。所有修改类方法开头都要检查，见 {@link #assertEditable}</li>
 *   <li><b>totalScore 永远是 paper_question 分值的累加</b>。
 *       任何会改变题目列表或分值的操作，都必须重新计算它</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperServiceImpl implements PaperService {

    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperVO create(PaperCreateDTO dto) {
        validateTimeRange(dto.getStartTime(), dto.getEndTime());

        Paper paper = new Paper();
        paper.setTitle(dto.getTitle());
        paper.setDuration(dto.getDuration());
        paper.setStartTime(dto.getStartTime());
        paper.setEndTime(dto.getEndTime());
        // 新建的试卷一定是草稿。不接受客户端指定状态 ——
        // 否则有人可以直接创建一张 PUBLISHED 的、零题目的试卷，
        // 绕过"发布前必须有题"的校验
        paper.setStatus(PaperStatus.DRAFT);
        // 总分初始为 0，组卷时重算
        paper.setTotalScore(0);
        // 创建人同样从登录态取
        paper.setCreatorId(UserContext.getUserId());

        paperMapper.insert(paper);

        log.info("创建试卷成功: id={}, title={}, creatorId={}",
                paper.getId(), paper.getTitle(), paper.getCreatorId());
        return PaperConverter.toVO(paper, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperVO update(Long id, PaperCreateDTO dto) {
        Paper paper = requirePaper(id);
        assertEditable(paper);
        validateTimeRange(dto.getStartTime(), dto.getEndTime());

        Paper update = new Paper();
        update.setId(id);
        update.setTitle(dto.getTitle());
        update.setDuration(dto.getDuration());
        update.setStartTime(dto.getStartTime());
        update.setEndTime(dto.getEndTime());

        paperMapper.updateById(update);

        Paper fresh = paperMapper.selectById(id);
        return PaperConverter.toVO(fresh, countQuestions(id));
    }

    // ==================== 组卷 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperDetailVO compose(Long id, PaperComposeDTO dto) {
        Paper paper = requirePaper(id);
        assertEditable(paper);

        List<PaperComposeDTO.PaperQuestionItem> items = dto.getQuestions();

        // ---------- 校验 1：请求内部不能有重复题目 ----------
        // 数据库上有 uk_paper_question 唯一索引兜底，但没有它的话
        // 报出来的是一个含糊的 Duplicate entry 异常。
        // 在应用层先查一遍，能给出「哪道题重复了」这种具体信息
        Set<Long> seen = new HashSet<>();
        for (PaperComposeDTO.PaperQuestionItem item : items) {
            if (!seen.add(item.getQuestionId())) {
                throw new BizException(ResultCode.PAPER_QUESTION_DUPLICATE,
                        "题目 ID " + item.getQuestionId() + " 在请求中出现了多次");
            }
        }

        // ---------- 校验 2：题目必须真实存在 ----------
        List<Long> questionIds = new ArrayList<>(seen);
        // selectBatchIds 是一次 IN 查询，不是循环单查 —— 注意别写成 for + selectById
        List<Question> questions = questionMapper.selectBatchIds(questionIds);

        // 数量对不上说明有 ID 不存在，或者对应的题目已被逻辑删除
        // （@TableLogic 会让已删除的题目查不出来）
        if (questions.size() != questionIds.size()) {
            Set<Long> foundIds = questions.stream()
                    .map(Question::getId)
                    .collect(Collectors.toSet());
            Set<Long> missing = new LinkedHashSet<>(questionIds);
            missing.removeAll(foundIds);
            throw new BizException(ResultCode.QUESTION_NOT_FOUND,
                    "以下题目不存在或已被删除：" + missing);
        }

        // ---------- 校验 3：分值必须为正 ----------
        // DTO 上已经用 @Min(1) 限过，但那只在 Controller 层由 @Valid 触发。
        // Service 作为公开方法，应该假设自己可能被别的入口调用
        // （定时任务、其他 Service），所以关键约束要在这里再确认一次。
        // 这叫"Service 层的自我防御"，代价是几行代码，
        // 收益是任何调用方都钻不了空子
        int totalScore = 0;
        for (PaperComposeDTO.PaperQuestionItem item : items) {
            if (item.getScore() == null || item.getScore() <= 0) {
                throw new BizException(ResultCode.PARAM_ERROR,
                        "题目 " + item.getQuestionId() + " 的分值必须大于 0");
            }
            totalScore += item.getScore();
        }

        // ---------- 执行：全量替换 ----------
        // 先删后插。两步都在同一个 @Transactional 里，
        // 中间失败会整体回滚，不会留下"删了但没插"的半截状态
        paperQuestionMapper.delete(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, id));

        List<PaperQuestion> relations = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            PaperComposeDTO.PaperQuestionItem item = items.get(i);
            PaperQuestion rel = new PaperQuestion();
            rel.setPaperId(id);
            rel.setQuestionId(item.getQuestionId());
            rel.setScore(item.getScore());
            // 没传顺序就按提交的列表顺序自动编号（从 1 开始，
            // 因为"第 0 题"对用户来说很别扭）
            rel.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : i + 1);
            relations.add(rel);
        }
        paperQuestionMapper.insertBatch(relations);

        // ---------- 重算总分 ----------
        // 这是"派生值必须跟着源头变"的落地点。
        // 忘了这一步的话，教师改完分值后，试卷列表上显示的总分还是旧的
        Paper paperUpdate = new Paper();
        paperUpdate.setId(id);
        paperUpdate.setTotalScore(totalScore);
        paperMapper.updateById(paperUpdate);

        log.info("组卷成功: paperId={}, 题目数={}, 总分={}", id, relations.size(), totalScore);

        return buildDetail(id);
    }

    // ==================== 状态流转 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperDetailVO publish(Long id) {
        Paper paper = requirePaper(id);
        assertEditable(paper);

        int count = countQuestions(id);
        if (count == 0) {
            // 允许发布空试卷的后果：学生会看到一张没有任何题目的试卷，
            // 只能直接交卷拿 0 分。与其让他们困惑，不如在发布时就拦住
            throw new BizException(ResultCode.PAPER_EMPTY);
        }

        // 发布时间点也要校验一次：创建时可能是合法的，
        // 但教师可能放了两天再来发布，那时 startTime 已经过去了
        validateTimeRange(paper.getStartTime(), paper.getEndTime());

        Paper update = new Paper();
        update.setId(id);
        update.setStatus(PaperStatus.PUBLISHED);
        paperMapper.updateById(update);

        log.info("发布试卷成功: paperId={}, 题目数={}, 总分={}", id, count, paper.getTotalScore());
        return buildDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Paper paper = requirePaper(id);

        // 已发布的试卷不能删。
        //
        // 这和"已发布不能改内容"是同一条规则的两个面：
        // 一旦发布，学生就可能已经看到甚至开考了。
        // 此时删掉试卷，那些考试记录会指向一个不存在的试卷。
        //
        // 注意这是【比题目删除更严格】的规则 ——
        // 题目只要没被引用就能删，而试卷只要发布过就不让删。
        // 因为试卷是考试的直接依据，而题目只是素材
        if (paper.getStatus() != PaperStatus.DRAFT) {
            throw new BizException(ResultCode.PAPER_NOT_EDITABLE,
                    "只有草稿状态的试卷可以删除，当前状态：" + paper.getStatus().getLabel());
        }

        // 先删关联行，再删试卷。
        // 虽然试卷是逻辑删除、关联行是物理删除，但顺序仍然重要：
        // 留着孤儿关联行会让 countByPaperIds 统计出错误的数量
        paperQuestionMapper.delete(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, id));
        paperMapper.deleteById(id);

        log.info("删除试卷: id={}, title={}", id, paper.getTitle());
    }

    // ==================== 查询 ====================

    @Override
    public PaperDetailVO getDetail(Long id) {
        requirePaper(id);
        return buildDetail(id);
    }

    @Override
    public PageResult<PaperVO> page(PaperQueryDTO query) {
        LambdaQueryWrapper<Paper> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getKeyword()), Paper::getTitle, query.getKeyword())
                .eq(query.getStatus() != null, Paper::getStatus, query.getStatus())
                .eq(query.getCreatorId() != null, Paper::getCreatorId, query.getCreatorId())
                .orderByDesc(Paper::getCreateTime);

        Page<Paper> page = new Page<>(query.getPage(), query.getSize());
        IPage<Paper> result = paperMapper.selectPage(page, wrapper);

        List<Paper> papers = result.getRecords();
        // 注意：这里【不能】用 PageResult.of(result, converter)，
        // 因为那个方法的转换函数只能看到单条记录，拿不到"批量统计出来的题目数"。
        // 需要先做一次批量聚合查询，再手工组装 PageResult
        Map<Long, Integer> countMap = countQuestionsBatch(papers);

        List<PaperVO> vos = papers.stream()
                .map(p -> PaperConverter.toVO(p, countMap.getOrDefault(p.getId(), 0)))
                .collect(Collectors.toList());

        return PageResult.of(result, vos);
    }

    // ==================== 私有辅助 ====================

    /**
     * 查出试卷，不存在则抛异常。
     * <p>
     * 抽成方法而不是每处都写 {@code if (paper == null) throw ...}，
     * 是为了保证<b>判断和报错信息在所有接口里完全一致</b>。
     * 复制粘贴的判空容易在某处漏掉，或者报出不一样的错误码。
     */
    private Paper requirePaper(Long id) {
        Paper paper = paperMapper.selectById(id);
        if (paper == null) {
            throw new BizException(ResultCode.PAPER_NOT_FOUND);
        }
        return paper;
    }

    /**
     * 断言试卷处于可编辑状态。
     * <p>
     * 这个方法存在的意义是：把「已发布不能改」这条规则<b>写成一个有名字的动作</b>。
     * 每个修改类方法开头调用一次，一眼就能看出这个方法的适用前提。
     * <p>
     * 如果改成在每个方法里展开写 {@code if (paper.getStatus() != DRAFT) { throw ... }}，
     * 第一是重复，第二是将来规则变了（比如允许 FINISHED 状态也能改）
     * 要改很多处，漏一处就是一个能绕过限制的接口。
     */
    private void assertEditable(Paper paper) {
        if (!paper.getStatus().isEditable()) {
            throw new BizException(ResultCode.PAPER_NOT_EDITABLE,
                    "试卷当前状态为「" + paper.getStatus().getLabel() + "」，内容已冻结");
        }
    }

    /** 校验开考时间早于截止时间。两者都可为 null（表示不限制） */
    private void validateTimeRange(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        // 只有在两个时间都填了的情况下才需要比较。
        // 这种判断顺序（先判可空，再比较）能避免 NullPointerException，
        // 也让"不限制时间"这个合法场景自然通过
        if (start != null && end != null && !start.isBefore(end)) {
            throw new BizException(ResultCode.PAPER_TIME_INVALID);
        }
    }

    /** 统计某张试卷的题目数 */
    private int countQuestions(Long paperId) {
        Long count = paperQuestionMapper.selectCount(
                new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, paperId));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 批量统计多张试卷的题目数，返回 {@code paperId -> count}。
     * <p>
     * 对应 {@link PaperQuestionMapper#countByPaperIds} 里讲的 N+1 问题。
     * 空列表要提前返回 —— 否则会拼出一条 {@code WHERE paper_id IN ()} 的语法错误 SQL。
     */
    private Map<Long, Integer> countQuestionsBatch(List<Paper> papers) {
        if (papers.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = papers.stream().map(Paper::getId).collect(Collectors.toList());

        return paperQuestionMapper.countByPaperIds(ids).stream()
                .collect(Collectors.toMap(
                        PaperQuestionCount::getPaperId,
                        PaperQuestionCount::getQuestionCount,
                        // 合并函数。有了 GROUP BY，同一个 paperId 不可能出现两次，
                        // 但 toMap 的重载要求必须传一个，这里保留第一个即可
                        (a, b) -> a));
    }

    /**
     * 组装试卷详情。
     * <p>
     * 这里用了两条查询 + 应用层合并，而不是一条 JOIN：
     * <ol>
     *   <li>查 paper_question 关联行（含分值、顺序）</li>
     *   <li>用关联行里的 questionId 批量查题目</li>
     * </ol>
     * 这样比 JOIN 多一次往返，但两条 SQL 都很简单、都能走索引，
     * 而且返回的对象能直接映射到实体，不必处理 JOIN 出来的重复列名。
     */
    private PaperDetailVO buildDetail(Long paperId) {
        Paper paper = paperMapper.selectById(paperId);

        // 按 sortOrder 升序 —— 学生看到的题目顺序必须稳定，
        // 否则同一个人刷新两次看到的题号都不一样
        List<PaperQuestion> relations = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<PaperQuestion>()
                        .eq(PaperQuestion::getPaperId, paperId)
                        .orderByAsc(PaperQuestion::getSortOrder));

        if (relations.isEmpty()) {
            return PaperConverter.toDetailVO(paper, List.of(), List.of());
        }

        List<Long> questionIds = relations.stream()
                .map(PaperQuestion::getQuestionId)
                .collect(Collectors.toList());
        List<Question> questions = questionMapper.selectBatchIds(questionIds);

        return PaperConverter.toDetailVO(paper, relations, questions);
    }
}
