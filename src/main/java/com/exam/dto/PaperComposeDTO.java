package com.exam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 组卷请求。全量替换语义：这张试卷的题目完全变成传上来的这一份，原来有、这次没传的会被删掉，所以重复调用结果一样。 */
@Data
@Schema(description = "组卷请求")
public class PaperComposeDTO {

    @Schema(description = "题目列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "试卷至少要有一道题")
    // @Valid 要加在集合上，否则 PaperQuestionItem 里的校验注解一个都不生效
    @Valid
    private List<PaperQuestionItem> questions;

    @Data
    @Schema(description = "组卷中的单道题")
    public static class PaperQuestionItem {

        @Schema(description = "题目 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "题目 ID 不能为空")
        private Long questionId;

        // 这道题在这张试卷里的分值，题库里存的那个只是建议值
        @Schema(description = "本题在本试卷中的分值", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
        @NotNull(message = "分值不能为空")
        @Min(value = 1, message = "单题分值至少为 1")
        @Max(value = 100, message = "单题分值不能超过 100")
        private Integer score;

        @Schema(description = "题目顺序，从 1 开始。不传则按列表顺序自动编号", example = "1")
        private Integer sortOrder;
    }
}
