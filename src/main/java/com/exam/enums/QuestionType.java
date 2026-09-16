package com.exam.enums;

import lombok.Getter;

/**
 * 题型。
 *
 * <h3>把「行为」挂在枚举上，而不是散落在 if-else 里</h3>
 * <p>
 * {@link #objective} 这个字段是故意放进来的。判断"这道题能不能自动判分"
 * 这件事，至少有四五个地方要用到：录入题目时校验答案格式、组卷时统计客观题总分、
 * 交卷时决定要不要走自动判分、成绩单上决定显示"待阅卷"还是具体分数。
 * <p>
 * 如果每个地方都写 {@code if (type == SINGLE || type == MULTIPLE || type == JUDGE)}，
 * 那么将来新增一种题型（比如填空题）时，<b>必须把所有地方都找出来改一遍</b>，
 * 漏掉任何一处就是一个 bug，而且编译器不会提醒你。
 * <p>
 * 把"这个题型是不是客观题"变成枚举自己的属性之后，新增题型时只要在这里填一个值，
 * 所有调用方自动就对了。这是「把数据和行为放在一起」的一个很小的例子，
 * 但它能实实在在地消灭一整类 bug。
 */
@Getter
public enum QuestionType {

    /** 单选题：选项互斥，答案形如 "A" */
    SINGLE("单选题", true),

    /** 多选题：答案形如 "A,B,D"，判分时忽略顺序 */
    MULTIPLE("多选题", true),

    /** 判断题：答案形如 "对" / "错" */
    JUDGE("判断题", true),

    /** 简答题：无选项，答案是一段参考答案，需人工阅卷 */
    ESSAY("简答题", false);

    /** 中文名，用于接口文档和前端展示 */
    private final String label;

    /**
     * 是否客观题（可由程序自动判分）。
     * <p>
     * 判断题和选择题的答案可以逐字符比对，简答题不行 —— 因为
     * 同一个意思可以有无数种表述，程序没法判断"运行速度较快"和"执行效率更高"
     * 是不是同一个答案。强行自动判分只会制造冤案。
     */
    private final boolean objective;

    QuestionType(String label, boolean objective) {
        this.label = label;
        this.objective = objective;
    }

    /** 该题型是否必须有选项 */
    public boolean needsOptions() {
        return this != ESSAY;
    }
}
