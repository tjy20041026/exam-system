package com.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 考试记录摘要，列表用。不带每题明细，那部分在 ExamResultVO 里。
 */
@Data
@Schema(description = "考试记录摘要（列表用）")
public class ExamRecordVO {

    @Schema(description = "考试记录 ID")
    private Long examRecordId;

    @Schema(description = "试卷 ID")
    private Long paperId;

    @Schema(description = "试卷标题")
    private String paperTitle;

    @Schema(description = "考试状态编码", example = "GRADED")
    private String status;

    @Schema(description = "考试状态中文", example = "已判卷")
    private String statusLabel;

    @Schema(description = "得分。未判卷时为 null —— 注意不是 0")
    private Integer score;

    @Schema(description = "试卷总分")
    private Integer totalScore;

    @Schema(description = "开考时间")
    private LocalDateTime startTime;

    @Schema(description = "交卷时间。未交卷为 null")
    private LocalDateTime submitTime;

    @Schema(description = "用时（秒）")
    private Integer durationUsed;

    @Schema(description = "是否可继续作答")
    private Boolean canContinue;
}
