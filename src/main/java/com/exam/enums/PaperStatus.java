package com.exam.enums;

import lombok.Getter;

/** 试卷状态。发布相当于内容冻结：有学生开考后就不能再改题和分值了，否则已有的考试记录失去依据。 */
@Getter
public enum PaperStatus {

    DRAFT("草稿"),

    PUBLISHED("已发布"),

    FINISHED("已结束");

    private final String label;

    PaperStatus(String label) {
        this.label = label;
    }

    /** 只有草稿能改题和分值 */
    public boolean isEditable() {
        return this == DRAFT;
    }

    public boolean isOpenForExam() {
        return this == PUBLISHED;
    }
}
