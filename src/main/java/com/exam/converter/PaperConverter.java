package com.exam.converter;

import com.exam.entity.Paper;
import com.exam.entity.PaperQuestion;
import com.exam.entity.Question;
import com.exam.vo.PaperDetailVO;
import com.exam.vo.PaperQuestionVO;
import com.exam.vo.PaperVO;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 试卷相关的转换。
 */
public final class PaperConverter {

    private PaperConverter() {
    }

    /**
     * 试卷 -> 概要 VO。
     *
     * @param questionCount 题目数量。由调用方查询后传入，
     *                      因为 Paper 实体本身不含这个字段（它不在 paper 表里）
     */
    public static PaperVO toVO(Paper paper, int questionCount) {
        if (paper == null) {
            return null;
        }
        PaperVO vo = new PaperVO();
        fill(vo, paper);
        vo.setQuestionCount(questionCount);
        return vo;
    }

    /**
     * 试卷 + 关联 + 题目 -> 详情 VO。
     *
     * <h3>为什么参数是「三个列表」而不是让调用方嵌套循环</h3>
     * <p>
     * 这里的关键是 {@code questionMap}：调用方先把题目列表转成一个
     * {@code Map<题目ID, 题目>}，然后在组装时 O(1) 查表。
     * <p>
     * 如果用嵌套循环（对每个 paper_question 去 questions 列表里找对应的题目），
     * 复杂度是 O(n²)。一张 50 题的试卷就是 2500 次比较 ——
     * 虽然这里 n 不大，但"用 Map 做关联"是一个应该条件反射般想到的模式。
     */
    public static PaperDetailVO toDetailVO(Paper paper,
                                           List<PaperQuestion> relations,
                                           List<Question> questions) {
        if (paper == null) {
            return null;
        }

        // 题目 ID -> 题目，便于下面 O(1) 查找
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity(), (a, b) -> a));

        List<PaperQuestionVO> items = relations.stream()
                .map(rel -> toPaperQuestionVO(rel, questionMap.get(rel.getQuestionId())))
                .collect(Collectors.toList());

        PaperDetailVO vo = new PaperDetailVO();
        fill(vo, paper);
        vo.setQuestionCount(items.size());
        vo.setQuestions(items);
        return vo;
    }

    /** 把 paper 的公共字段填进 VO，供上面两个方法复用 */
    private static void fill(PaperVO vo, Paper paper) {
        vo.setId(paper.getId());
        vo.setTitle(paper.getTitle());
        vo.setTotalScore(paper.getTotalScore());
        vo.setDuration(paper.getDuration());
        vo.setStartTime(paper.getStartTime());
        vo.setEndTime(paper.getEndTime());
        vo.setStatus(paper.getStatus());
        vo.setStatusLabel(paper.getStatus() == null ? null : paper.getStatus().getLabel());
        vo.setCreatorId(paper.getCreatorId());
        vo.setCreateTime(paper.getCreateTime());
    }

    /**
     * 把「关联行 + 题目」拼成一道题的视图。
     * <p>
     * 注意 {@code score} 取的是 {@code rel.getScore()}（本试卷中的分值），
     * 不是 {@code question.getScore()}（题库里的建议分值）。
     * 这两个值经常不同，取错了会让教师看到的分值和实际判分用的分值对不上。
     */
    private static PaperQuestionVO toPaperQuestionVO(PaperQuestion rel, Question question) {
        PaperQuestionVO vo = new PaperQuestionVO();
        vo.setQuestionId(rel.getQuestionId());
        vo.setSortOrder(rel.getSortOrder());
        // ← 关键：分值来自关联行
        vo.setScore(rel.getScore());

        if (question != null) {
            vo.setContent(question.getContent());
            vo.setType(question.getType());
            vo.setTypeLabel(question.getType() == null ? null : question.getType().getLabel());
            vo.setOptions(question.getOptions());
            vo.setAnswer(question.getAnswer());
            vo.setDifficulty(question.getDifficulty());
        }
        // question 为 null 的情况见 PaperServiceImpl 的说明：
        // 理论上不该发生，但返回 null 字段比抛异常更温和
        return vo;
    }
}
