package com.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 成绩单：各题作答与得分。
 * <p>
 * 刻意不返回标准答案 —— 同一张试卷的考试时间窗是重叠的，
 * 先交卷的人看到答案，等于给还没考的人泄题。
 */
@Data
@Schema(description = "成绩单（含各题作答与得分，不含标准答案）")
public class ExamResultVO {

    @Schema(description = "考试记录 ID")
    private Long examRecordId;

    @Schema(description = "试卷 ID")
    private Long paperId;

    @Schema(description = "试卷标题")
    private String paperTitle;

    @Schema(description = "状态编码", example = "GRADED")
    private String status;

    @Schema(description = "状态中文", example = "已判卷")
    private String statusLabel;

    @Schema(description = "实得分。未判卷为 null")
    private Integer score;

    @Schema(description = "试卷满分")
    private Integer totalScore;

    @Schema(description = "得分率（百分数）", example = "85.5")
    private Double scoreRate;

    @Schema(description = "开考时间")
    private LocalDateTime startTime;

    @Schema(description = "交卷时间")
    private LocalDateTime submitTime;

    @Schema(description = "用时（秒）")
    private Integer durationUsed;

    @Schema(description = "是否还有待人工阅卷的题目")
    private Boolean hasPendingEssay;

    @Schema(description = "各题作答明细")
    private List<AnswerDetailVO> details;

    @Data
    @Schema(description = "单题作答明细")
    public static class AnswerDetailVO {

        @Schema(description = "题号")
        private Integer sortOrder;

        @Schema(description = "题目 ID")
        private Long questionId;

        @Schema(description = "题干")
        private String content;

        @Schema(description = "题型中文")
        private String typeLabel;

        @Schema(description = "考生的答案")
        private String userAnswer;

        @Schema(description = "是否正确。null 表示待阅卷")
        private Boolean correct;

        @Schema(description = "本题得分")
        private Integer score;

        @Schema(description = "本题满分")
        private Integer fullScore;
    }
}
