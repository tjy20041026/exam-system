package com.exam.converter;

import com.exam.entity.Question;
import com.exam.vo.QuestionVO;

/**
 * {@code Question} → {@code QuestionVO} 转换。
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
     * 难度数字转中文。
     * <p>
     * <b>这里故意没有做成枚举。</b>难度在数据库里是 TINYINT(1/2/3)，
     * 而项目里其他枚举（角色、题型、试卷状态）对应的都是 VARCHAR 列，
     * 靠 MyBatis 按名字映射。如果给难度也建枚举，就得额外引入
     * {@code @EnumValue} 或自定义 TypeHandler 才能存成数字 ——
     * 为三个固定取值搭这一套机制，收益不抵复杂度。
     * <p>
     * 什么情况下值得升级成枚举：当难度需要携带更多行为时
     * （比如"难度的权重系数"用于自动组卷的难度分布计算）。
     * <b>现在不需要，就不提前做。</b>
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
