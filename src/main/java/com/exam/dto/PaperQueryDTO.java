package com.exam.dto;

import com.exam.enums.PaperStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 试卷查询条件，字段全可选，不传表示不过滤。 */
@Data
@Schema(description = "试卷查询条件")
public class PaperQueryDTO {

    @Schema(description = "标题关键字，模糊匹配", example = "期末")
    private String keyword;

    @Schema(description = "试卷状态", example = "PUBLISHED")
    private PaperStatus status;

    @Schema(description = "创建教师 ID")
    private Long creatorId;

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码最小为 1")
    private Long page = 1L;

    @Schema(description = "每页条数", example = "10")
    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 100, message = "每页最多 100 条")
    private Long size = 10L;
}
