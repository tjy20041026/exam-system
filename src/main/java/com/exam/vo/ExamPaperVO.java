package com.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试进行中的整份试卷：题目、截止时间、已答进度。开考和断点续考共用这一个接口。
 */
@Data
@Schema(description = "考试试卷（含题目、倒计时、已答进度）")
public class ExamPaperVO {

    @Schema(description = "考试记录 ID。后续所有答题、交卷请求都要带上它")
    private Long examRecordId;

    @Schema(description = "试卷 ID")
    private Long paperId;

    @Schema(description = "试卷标题")
    private String paperTitle;

    @Schema(description = "试卷总分")
    private Integer totalScore;

    @Schema(description = "开考时间")
    private LocalDateTime startTime;

    @Schema(description = "交卷截止时刻")
    private LocalDateTime deadline;

    @Schema(description = "剩余秒数。以服务端为准，不依赖客户端计时")
    private Long remainingSeconds;

    @Schema(description = "考试状态", example = "ONGOING")
    private String status;

    @Schema(description = "题目列表（不含答案），按题号升序")
    private List<ExamQuestionVO> questions;

    @Schema(description = "已作答内容，键为题目 ID")
    private java.util.Map<Long, String> savedAnswers;
}
