package com.exam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建试卷的请求参数。
 * <p>
 * <b>注意这里没有 totalScore 字段</b> —— 这是有意的。
 * 总分是各题分值累加出来的派生值，由后端在组卷时算好，不接受客户端设置。
 * 详见 {@code Paper.totalScore} 的注释。
 */
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
    // 上限设 600 分钟（10 小时）是为了拦住明显的误输入。
    // 教师本来想填 60，手滑多打一个 0 变成 600 还算能接受；
    // 但填成 6000 的话，那道题会一直躺在"进行中"状态，
    // 学生可以随时回来继续答 —— 实际上等于没有时限
    @Max(value = 600, message = "考试时长不能超过 600 分钟")
    private Integer duration;

    @Schema(description = "开考时间，不传表示不限制", example = "2026-09-20 09:00:00")
    private LocalDateTime startTime;

    @Schema(description = "截止时间，不传表示不限制", example = "2026-09-20 11:00:00")
    private LocalDateTime endTime;
}
