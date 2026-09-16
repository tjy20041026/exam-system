package com.exam.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/** 题目选项。用 record 而不用 Map，是因为选项顺序是刚性要求，JSON 对象不保证顺序。 */
@Schema(description = "题目选项")
public record QuestionOption(

        @Schema(description = "选项标识，通常是 A/B/C/D", example = "A")
        String key,

        @Schema(description = "选项内容", example = "抽象类不能实例化")
        String value

) {
    /** 两个字段都非空才算有效选项 */
    public boolean hasContent() {
        return key != null && !key.isBlank() && value != null && !value.isBlank();
    }
}
