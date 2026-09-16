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
 * <p>
 * 两条约束贯穿全类：已发布的试卷不能改（见 {@link #assertEditable}），
 * totalScore 永远是 paper_question 分值的累加，改题目列表或分值就得重算。
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
        // 新建一定是草稿，不接受客户端指定状态 —— 否则可以直接创建一张
        // PUBLISHED 的零题目试卷，绕过"发布前必须有题"的校验
        paper.setStatus(PaperStatus.DRAFT);
        paper.setTotalScore(0);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperDetailVO compose(Long id, PaperComposeDTO dto) {
        Paper paper = requirePaper(id);
        assertEditable(paper);

        List<PaperComposeDTO.PaperQuestionItem> items = dto.getQuestions();

        // 先在应用层查一遍重复题目：uk_paper_question 只能报出含糊的
        // Duplicate entry，这里能指出是哪道题重复了
        Set<Long> seen = new HashSet<>();
        for (PaperComposeDTO.PaperQuestionItem item : items) {
            if (!seen.add(item.getQuestionId())) {
                throw new BizException(ResultCode.PAPER_QUESTION_DUPLICATE,
                        "题目 ID " + item.getQuestionId() + " 在请求中出现了多次");
            }
        }

        List<Long> questionIds = new ArrayList<>(seen);
        // 一次 IN 查询，别写成 for + selectById
        List<Question> questions = questionMapper.selectBatchIds(questionIds);

        // 数量对不上说明有 ID 不存在，或者是已被逻辑删除的题目
        if (questions.size() != questionIds.size()) {
            Set<Long> foundIds = questions.stream()
                    .map(Question::getId)
                    .collect(Collectors.toSet());
            Set<Long> missing = new LinkedHashSet<>(questionIds);
            missing.removeAll(foundIds);
            throw new BizException(ResultCode.QUESTION_NOT_FOUND,
                    "以下题目不存在或已被删除：" + missing);
        }

        // DTO 上的 @Min(1) 只在 Controller 的 @Valid 里生效。Service 可能被
        // 定时任务之类的入口直接调用，关键约束在这里再确认一次
        int totalScore = 0;
        for (PaperComposeDTO.PaperQuestionItem item : items) {
            if (item.getScore() == null || item.getScore() <= 0) {
                throw new BizException(ResultCode.PARAM_ERROR,
                        "题目 " + item.getQuestionId() + " 的分值必须大于 0");
            }
            totalScore += item.getScore();
        }

        // 全量替换：先删后插，两步在同一个事务里，失败一起回滚，
        // 不会留下"删了但没插"的半截状态
        paperQuestionMapper.delete(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, id));

        List<PaperQuestion> relations = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            PaperComposeDTO.PaperQuestionItem item = items.get(i);
            PaperQuestion rel = new PaperQuestion();
            rel.setPaperId(id);
            rel.setQuestionId(item.getQuestionId());
            rel.setScore(item.getScore());
            // 没传顺序就按提交的列表顺序编号，从 1 开始
            rel.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : i + 1);
            relations.add(rel);
        }
        paperQuestionMapper.insertBatch(relations);

        // 重算总分。漏了这步，教师改完分值后列表上显示的还是旧总分
        Paper paperUpdate = new Paper();
        paperUpdate.setId(id);
        paperUpdate.setTotalScore(totalScore);
        paperMapper.updateById(paperUpdate);

        log.info("组卷成功: paperId={}, 题目数={}, 总分={}", id, relations.size(), totalScore);

        return buildDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperDetailVO publish(Long id) {
        Paper paper = requirePaper(id);
        assertEditable(paper);

        int count = countQuestions(id);
        if (count == 0) {
            // 空试卷让学生只能直接交卷拿 0 分，不如发布时就拦住
            throw new BizException(ResultCode.PAPER_EMPTY);
        }

        // 再校验一次时间：教师可能放了两天才来发布，那时 startTime 已经过去了
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

        // 发布过就不能删：学生可能已经看到甚至开考了，删掉之后那些考试记录
        // 会指向一个不存在的试卷。这比题目删除更严格 —— 题目只要没被引用就能删，
        // 而试卷是考试的直接依据
        if (paper.getStatus() != PaperStatus.DRAFT) {
            throw new BizException(ResultCode.PAPER_NOT_EDITABLE,
                    "只有草稿状态的试卷可以删除，当前状态：" + paper.getStatus().getLabel());
        }

        // 先删关联行再删试卷：留着孤儿关联行会让 countByPaperIds 数出错误的数量
        paperQuestionMapper.delete(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, id));
        paperMapper.deleteById(id);

        log.info("删除试卷: id={}, title={}", id, paper.getTitle());
    }

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
        // 不能用 PageResult.of(result, converter)：那个转换函数只看得到单条记录，
        // 拿不到批量统计出来的题目数，得先聚合再手工组装
        Map<Long, Integer> countMap = countQuestionsBatch(papers);

        List<PaperVO> vos = papers.stream()
                .map(p -> PaperConverter.toVO(p, countMap.getOrDefault(p.getId(), 0)))
                .collect(Collectors.toList());

        return PageResult.of(result, vos);
    }

    /** 查试卷，不存在就抛。抽出来是为了所有接口的判断和错误码保持一致 */
    private Paper requirePaper(Long id) {
        Paper paper = paperMapper.selectById(id);
        if (paper == null) {
            throw new BizException(ResultCode.PAPER_NOT_FOUND);
        }
        return paper;
    }

    /**
     * 断言试卷可编辑。把"已发布不能改"写成一个有名字的动作，
     * 每个修改类方法开头调一次，规则变了也只改这一处。
     */
    private void assertEditable(Paper paper) {
        if (!paper.getStatus().isEditable()) {
            throw new BizException(ResultCode.PAPER_NOT_EDITABLE,
                    "试卷当前状态为「" + paper.getStatus().getLabel() + "」，内容已冻结");
        }
    }

    /** 校验开考时间早于截止时间。两者都可为 null，表示不限制 */
    private void validateTimeRange(java.time.LocalDateTime start, java.time.LocalDateTime end) {
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
     * 空列表要提前返回，否则会拼出 {@code WHERE paper_id IN ()} 这种语法错误 SQL。
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
                        // 有了 GROUP BY 同一个 paperId 不会出现两次，这里只是 toMap 要求传
                        (a, b) -> a));
    }

    /**
     * 组装试卷详情。两条查询 + 应用层合并，而不是一条 JOIN：
     * 比 JOIN 多一次往返，但两条 SQL 都简单、都能走索引，
     * 返回的对象也能直接映射到实体，不用处理 JOIN 出来的重复列名。
     */
    private PaperDetailVO buildDetail(Long paperId) {
        Paper paper = paperMapper.selectById(paperId);

        // 按 sortOrder 升序：顺序不稳定的话，刷新两次看到的题号都不一样
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
