package com.exam.enums;

import lombok.Getter;

/**
 * 考试记录状态。SUBMITTED 和 GRADED 分开，是因为简答题不能自动判分：
 * 交卷时只出客观题的分，直接标成"完成"学生会以为自己就考了这么点。
 */
@Getter
public enum ExamStatus {

    ONGOING("进行中"),

    SUBMITTED("已交卷，待阅卷"),

    GRADED("已判卷"),

    TIMEOUT("超时自动交卷");

    private final String label;

    ExamStatus(String label) {
        this.label = label;
    }

    public boolean isFinished() {
        return this != ONGOING;
    }

    public boolean isScoreFinal() {
        return this == GRADED;
    }
}
