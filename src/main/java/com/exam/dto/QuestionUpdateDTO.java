package com.exam.dto;

import com.exam.entity.QuestionOption;
import com.exam.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 修改题目请求。没和 QuestionCreateDTO 共用一个类，因为约束不一样：创建时字段必填，这里不传表示不改。 */
@Data
@Schema(description = "修改题目请求，只传需要改的字段")
public class QuestionUpdateDTO {

    @Schema(description = "题干，不传表示不修改")
    @Size(max = 1000, message = "题干不能超过 1000 字")
    private String content;

    @Schema(description = "题型，不传表示不修改")
    private QuestionType type;

    @Schema(description = "选项列表，不传表示不修改。传空数组表示清空选项")
    private List<QuestionOption> options;

    @Schema(description = "标准答案，不传表示不修改")
    @Size(max = 500, message = "答案不能超过 500 字")
    private String answer;

    @Schema(description = "建议分值，不传表示不修改")
    @Min(value = 1, message = "分值至少为 1")
    @Max(value = 100, message = "单题分值不能超过 100")
    private Integer score;

    @Schema(description = "难度：1 易，2 中，3 难，不传表示不修改")
    @Min(value = 1, message = "难度只能是 1/2/3")
    @Max(value = 3, message = "难度只能是 1/2/3")
    private Integer difficulty;
}
