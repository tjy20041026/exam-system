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

    /** 试卷 -> 概要 VO。questionCount 由调用方查好传进来，Paper 实体里没有这个字段。 */
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
     * 调用方先把题目列表转成 Map，这里按 ID 查表，免得对每条关联行再去遍历题目列表。
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

    /** 关联行 + 题目 -> 一道题的视图 */
    private static PaperQuestionVO toPaperQuestionVO(PaperQuestion rel, Question question) {
        PaperQuestionVO vo = new PaperQuestionVO();
        vo.setQuestionId(rel.getQuestionId());
        vo.setSortOrder(rel.getSortOrder());
        // 分值取关联行的，不是题库里的建议分值，这两个值经常不一样
        vo.setScore(rel.getScore());

        if (question != null) {
            vo.setContent(question.getContent());
            vo.setType(question.getType());
            vo.setTypeLabel(question.getType() == null ? null : question.getType().getLabel());
            vo.setOptions(question.getOptions());
            vo.setAnswer(question.getAnswer());
            vo.setDifficulty(question.getDifficulty());
        }
        // question 为 null 属于理论上不该发生的情况，返回空字段比抛异常温和
        return vo;
    }
}
