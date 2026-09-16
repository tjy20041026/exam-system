package com.exam.dto;

import com.exam.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 题库查询条件，字段全可选，不传表示不过滤。 */
@Data
@Schema(description = "题库查询条件")
public class QuestionQueryDTO {

    @Schema(description = "题干关键字，模糊匹配", example = "抽象类")
    private String keyword;

    @Schema(description = "题型", example = "SINGLE")
    private QuestionType type;

    @Schema(description = "难度：1 易，2 中，3 难", example = "2")
    private Integer difficulty;

    @Schema(description = "出题教师 ID。教师查自己的题时传入")
    private Long creatorId;

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码最小为 1")
    private Long page = 1L;

    @Schema(description = "每页条数", example = "10")
    @Min(value = 1, message = "每页至少 1 条")
    // 不设上限的话 size 传大一点就能把全表捞出来
    @Max(value = 100, message = "每页最多 100 条")
    private Long size = 10L;
}
