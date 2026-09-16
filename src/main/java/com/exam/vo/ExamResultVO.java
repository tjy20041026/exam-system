package com.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 成绩单 —— 交卷后能看到的东西。
 *
 * <h3>⚠️ 这里刻意【不】返回标准答案</h3>
 * <p>
 * 这是本类最重要的设计决定，也是我犹豫过的地方 ——
 * 直觉上"考完试给学生看答案"是很自然的功能，但在这个系统里它有个漏洞：
 * <p>
 * 同一张试卷会被<b>很多学生</b>考，而且考试时间窗口是重叠的
 * （比如"期中考试"开放 3 天）。如果学生甲今天上午交卷就能看到全部答案，
 * 他下午把答案告诉学生乙，这场考试就失去意义了。
 * <p>
 * 所以这里只返回：
 * <ul>
 *   <li>考生自己的答案 —— 他自己写过的东西，返回给他没有风险</li>
 *   <li>对错判定和得分 —— 让他知道哪题错了</li>
 * </ul>
 * 但不返回标准答案。
 * <p>
 * <b>正确的"看答案"功能该怎么实现</b>：等整场考试的
 * {@code paper.end_time} 过去之后再开放，或者由教师手动把试卷置为 FINISHED。
 * 那时候所有学生都交完卷了，公开答案就没有泄题风险。
 * 这个判断需要读 {@code paper} 表，留到 Day 6 之后再做 ——
 * <b>与其现在实现一个"看起来能用但会泄题"的版本，不如先不做。</b>
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

    /** 总分。判卷未完成时为 null，前端应显示"待阅卷"而不是 0 */
    @Schema(description = "实得分。未判卷为 null")
    private Integer score;

    @Schema(description = "试卷满分")
    private Integer totalScore;

    /**
     * 得分率，0~100 的百分数，保留一位小数。
     * <p>
     * 服务端算好给前端，而不是让前端拿 score/totalScore 自己除 ——
     * 除法的边界情况（除数为 0、除不尽）在每一端都要处理一遍，
     * 迟早有一端忘了判空。
     */
    @Schema(description = "得分率（百分数）", example = "85.5")
    private Double scoreRate;

    @Schema(description = "开考时间")
    private LocalDateTime startTime;

    @Schema(description = "交卷时间")
    private LocalDateTime submitTime;

    @Schema(description = "用时（秒）")
    private Integer durationUsed;

    /**
     * 是否还有简答题等待人工阅卷。
     * <p>
     * 前端据此提示"成绩为暂定，简答题批阅后可能变动"。
     * <p>
     * 这个字段如果不说清楚，学生会以为眼前这个分数就是最终成绩 ——
     * <b>让用户误解系统状态，本身就是一种缺陷</b>，哪怕程序逻辑完全正确。
     */
    @Schema(description = "是否还有待人工阅卷的题目")
    private Boolean hasPendingEssay;

    @Schema(description = "各题作答明细")
    private List<AnswerDetailVO> details;

    /**
     * 单题作答明细。
     * <p>
     * 用静态内部类而不是再开一个文件 —— 它和成绩单是强绑定的，
     * 脱离成绩单没有任何意义。<b>生命周期一致的类型放在一起，比散落两个文件更好找。</b>
     */
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

        /** null = 待人工阅卷，true/false = 已判定 */
        @Schema(description = "是否正确。null 表示待阅卷")
        private Boolean correct;

        @Schema(description = "本题得分")
        private Integer score;

        @Schema(description = "本题满分")
        private Integer fullScore;
    }
}
