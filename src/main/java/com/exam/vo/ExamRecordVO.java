package com.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 考试记录摘要 —— 给「我的考试列表」用。
 *
 * <h3>为什么不复用 ExamResultVO</h3>
 * <p>
 * 列表页会一次返回几十条记录。成绩单（{@link ExamResultVO}）里带着每题明细，
 * 如果列表也返回明细，一个学生的 10 场考试就是 10 份完整答卷 ——
 * 数据量翻了几十倍，而前端在列表页根本用不到。
 * <p>
 * 这是典型的<b>列表 VO 和详情 VO 分离</b>：列表只给 summary，
 * 想看细节再调详情接口。代价是多一个类，换来的是列表接口的响应体
 * 不随明细字段增长而膨胀。
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

    /**
     * 是否还能继续作答。
     * <p>
     * 前端据此决定列表里那一行显示「继续考试」还是「查看成绩」。
     * <p>
     * 由服务端算好返回，而不是让前端自己拼接判断逻辑 ——
     * "什么时候算能继续考"是业务规则（要看状态、还要看有没有超时），
     * 这种规则散落到多个客户端里，改一次就要改好几处，
     * 而且各端实现不一致会造成行为差异。<b>业务规则放在服务端。</b>
     */
    @Schema(description = "是否可继续作答")
    private Boolean canContinue;
}
