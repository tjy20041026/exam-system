package com.exam.enums;

import lombok.Getter;

/**
 * 试卷状态。
 *
 * <h3>状态机：为什么试卷需要"发布"这一步</h3>
 * <p>
 * 如果 {@code DRAFT} 和 {@code PUBLISHED} 不分开，教师一边组卷、学生一边考试，
 * 就会出现「题目加到一半，学生已经开考了」的情况 —— 那些学生考的是哪一版？
 * 考试考到一半，教师删了一道题，正在答题的学生会怎样？
 * <p>
 * 发布这一步相当于一个<b>快照承诺</b>：一旦发布，试卷内容就冻结了，
 * 学生的考试体验有了确定的基准。这也是为什么下面很多操作都要求"未发布的试卷才能改"。
 *
 * <h3>状态流转</h3>
 * <pre>
 *   DRAFT ──发布──▶ PUBLISHED ──到达截止时间──▶ FINISHED
 *     ▲                  │
 *     └────撤回（仅限无人开考时）────┘
 * </pre>
 * <p>
 * <b>注意这里没有"从 PUBLISHED 改回 DRAFT"的直接路径。</b>
 * 因为一旦有学生开考，撤回会让已经存在的考试记录失去依据
 * （试卷变了，那些学生考的到底是什么？）。
 * 真要撤回，必须满足"没有任何考试记录"这个前提 ——
 * 这类「带前置条件的流转」正是状态机存在的意义，
 * 它把业务规则从"大家记得别这么做"变成了"代码不让你这么做"。
 */
@Getter
public enum PaperStatus {

    /** 草稿：可自由增删题目、改分值 */
    DRAFT("草稿"),

    /** 已发布：内容冻结，学生可以开考 */
    PUBLISHED("已发布"),

    /** 已结束：过了截止时间，不再接受新开考 */
    FINISHED("已结束");

    private final String label;

    PaperStatus(String label) {
        this.label = label;
    }

    /** 是否允许修改试卷内容（题目、分值） */
    public boolean isEditable() {
        return this == DRAFT;
    }

    /** 是否允许学生开考 */
    public boolean isOpenForExam() {
        return this == PUBLISHED;
    }
}
