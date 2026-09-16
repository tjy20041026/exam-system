package com.exam.vo;

import com.exam.enums.PaperStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 试卷概要，用于列表展示，不含题目列表。
 */
@Data
@Schema(description = "试卷概要")
public class PaperVO {

    @Schema(description = "试卷 ID")
    private Long id;

    @Schema(description = "试卷标题")
    private String title;

    @Schema(description = "总分。由各题分值累加得出，不接受客户端设置")
    private Integer totalScore;

    @Schema(description = "考试时长（分钟）")
    private Integer duration;

    @Schema(description = "开考时间，null 表示不限制")
    private LocalDateTime startTime;

    @Schema(description = "截止时间，null 表示不限制")
    private LocalDateTime endTime;

    @Schema(description = "状态")
    private PaperStatus status;

    @Schema(description = "状态中文名", example = "已发布")
    private String statusLabel;

    @Schema(description = "题目数量")
    private Integer questionCount;

    @Schema(description = "创建教师 ID")
    private Long creatorId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
