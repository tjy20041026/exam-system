package com.exam.dto;

import com.exam.entity.QuestionOption;
import com.exam.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 录入题目请求。跨字段的业务规则（单选题的答案必须命中某个选项等）注解表达不了，在 QuestionServiceImpl.validateByType() 里手写。 */
@Data
@Schema(description = "录入题目请求")
public class QuestionCreateDTO {

    @Schema(description = "题干", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "下列关于抽象类的说法，正确的是？")
    @NotBlank(message = "题干不能为空")
    @Size(max = 1000, message = "题干不能超过 1000 字")
    private String content;

    @Schema(description = "题型", requiredMode = Schema.RequiredMode.REQUIRED, example = "SINGLE")
    @NotNull(message = "题型不能为空")
    private QuestionType type;

    @Schema(description = "选项列表。选择题必填，简答题留空")
    private List<QuestionOption> options;

    @Schema(description = "标准答案。单选填 \"A\"，多选填 \"A,B,D\"，判断填 \"对\"/\"错\"，简答填参考答案",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "A")
    @NotBlank(message = "答案不能为空")
    @Size(max = 500, message = "答案不能超过 500 字")
    private String answer;

    @Schema(description = "建议分值", example = "5")
    @NotNull(message = "分值不能为空")
    @Min(value = 1, message = "分值至少为 1")
    @Max(value = 100, message = "单题分值不能超过 100")
    private Integer score;

    @Schema(description = "难度：1 易，2 中，3 难", example = "2")
    @NotNull(message = "难度不能为空")
    @Min(value = 1, message = "难度只能是 1/2/3")
    @Max(value = 3, message = "难度只能是 1/2/3")
    private Integer difficulty;
}
