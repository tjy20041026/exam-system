package com.exam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** 创建试卷请求。没有 totalScore：总分是各题分值累加出来的，组卷时由后端算，不接受客户端设置。 */
@Data
@Schema(description = "创建试卷请求")
public class PaperCreateDTO {

    @Schema(description = "试卷标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "Java 基础期末考试")
    @NotBlank(message = "试卷标题不能为空")
    @Size(max = 200, message = "标题不能超过 200 字")
    private String title;

    @Schema(description = "考试时长（分钟）", requiredMode = Schema.RequiredMode.REQUIRED, example = "60")
    @NotNull(message = "考试时长不能为空")
    @Min(value = 1, message = "考试时长至少 1 分钟")
    // 上限 600 分钟，拦一下手滑多打一个 0 的输入
    @Max(value = 600, message = "考试时长不能超过 600 分钟")
    private Integer duration;

    @Schema(description = "开考时间，不传表示不限制", example = "2026-09-20 09:00:00")
    private LocalDateTime startTime;

    @Schema(description = "截止时间，不传表示不限制", example = "2026-09-20 11:00:00")
    private LocalDateTime endTime;
}
