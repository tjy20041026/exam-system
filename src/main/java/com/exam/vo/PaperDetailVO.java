package com.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 试卷详情，含完整题目列表。<b>仅供教师查看</b>。
 *
 * <h3>用继承复用字段，但要清楚它的代价</h3>
 * <p>
 * 这里继承 {@link PaperVO} 来复用那一堆字段，省去了重新声明一遍。
 * 在 VO 这个场景下是合适的，因为详情<b>确实是</b>概要的超集 ——
 * 满足"is-a"关系（一张试卷详情就是一张试卷概要加上题目列表）。
 * <p>
 * 但要留意继承的固有代价：<b>父类改字段会波及所有子类</b>。
 * 如果将来 {@code PaperVO} 加了一个只对列表有意义、对详情没意义的字段，
 * 它也会出现在详情的响应里。
 * <p>
 * 判断标准是：子类能无条件替换父类吗？能，就用继承；
 * 只是为了少写几个字段名，就老老实实复制 —— 那种情况下继承带来的耦合
 * 迟早会咬你一口。
 *
 * <h3>⚠️ 这个 VO 带着所有题目的答案，绝不能返回给学生</h3>
 * <p>
 * 学生考试时要用的题目列表必须另建一个不含 {@code answer} 的 VO。
 * 详见 Day 5 的考试接口实现。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "试卷详情（含题目与答案，仅供教师使用）")
public class PaperDetailVO extends PaperVO {

    @Schema(description = "题目列表，按 sortOrder 升序")
    private List<PaperQuestionVO> questions;
}
