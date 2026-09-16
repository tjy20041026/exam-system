package com.exam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 保存单题答案的入参。userAnswer 不加 @NotBlank，空串表示学生主动清空这道题，和从没答过（null）是两回事；格式是单选/判断传 "A"/"对"，多选传 "A,B,C"，简答传原文。 */
@Data
@Schema(description = "保存单题答案")
public class SaveAnswerDTO {

    @NotNull(message = "题目 ID 不能为空")
    @Schema(description = "题目 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long questionId;

    @Schema(description = "考生答案。允许为空字符串（表示清空该题答案）", example = "A")
    private String userAnswer;
}
