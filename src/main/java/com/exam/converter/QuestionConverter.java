package com.exam.converter;

import com.exam.entity.Question;
import com.exam.vo.QuestionVO;

/**
 * Question -> QuestionVO 转换。
 */
public final class QuestionConverter {

    private QuestionConverter() {
    }

    public static QuestionVO toVO(Question q) {
        if (q == null) {
            return null;
        }
        QuestionVO vo = new QuestionVO();
        vo.setId(q.getId());
        vo.setContent(q.getContent());
        vo.setType(q.getType());
        vo.setTypeLabel(q.getType() == null ? null : q.getType().getLabel());
        vo.setOptions(q.getOptions());
        vo.setAnswer(q.getAnswer());
        vo.setScore(q.getScore());
        vo.setDifficulty(q.getDifficulty());
        vo.setDifficultyLabel(difficultyLabel(q.getDifficulty()));
        vo.setCreatorId(q.getCreatorId());
        vo.setCreateTime(q.getCreateTime());
        return vo;
    }

    /**
     * 难度数字转中文。故意没做成枚举：库里是 TINYINT，其他枚举都是 VARCHAR 按名字映射，
     * 给它单独引入 @EnumValue 或 TypeHandler 不划算，等需要带行为（比如组卷权重）再说。
     */
    private static String difficultyLabel(Integer difficulty) {
        if (difficulty == null) {
            return null;
        }
        return switch (difficulty) {
            case 1 -> "易";
            case 2 -> "中";
            case 3 -> "难";
            default -> "未知";
        };
    }
}
