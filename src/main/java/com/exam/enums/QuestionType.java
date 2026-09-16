package com.exam.enums;

import lombok.Getter;

/** 题型。objective 挂在枚举上而不是写 if-else，加题型时只需要在这儿填个值。 */
@Getter
public enum QuestionType {

    SINGLE("单选题", true),

    MULTIPLE("多选题", true),

    JUDGE("判断题", true),

    /** 无选项，参考答案文本，人工阅卷 */
    ESSAY("简答题", false);

    private final String label;

    /** 能否由程序自动判分 */
    private final boolean objective;

    QuestionType(String label, boolean objective) {
        this.label = label;
        this.objective = objective;
    }

    public boolean needsOptions() {
        return this != ESSAY;
    }
}
