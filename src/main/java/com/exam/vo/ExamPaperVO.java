package com.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试进行中的整份试卷视图 —— 学生答题界面需要的一切。
 *
 * <h3>为什么要把「剩余时间」和「已答答案」都放在这个接口里</h3>
 * <p>
 * 因为这个接口要同时承担两个职责：
 * <ol>
 *   <li><b>开考</b>：第一次调用，返回题目和倒计时</li>
 *   <li><b>断点续考</b>：调用同一个接口，返回题目 + 倒计时 + 之前答过的内容</li>
 * </ol>
 * <p>
 * 用同一个接口而不是两个，好处是客户端只需要一套渲染逻辑：
 * 拿到什么就画什么，不需要区分"这是新考试"还是"这是恢复的考试"。
 * <p>
 * Day 6 会用 Redis 实现断点续考，到时候 {@code savedAnswers} 就是从
 * {@code exam:session:{id}} 这个 Hash 里读出来的。
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

    /**
     * 截止时刻（绝对时间）。
     * <p>
     * 和 remainingSeconds 一起返回是<b>有意为之的双保险</b>：
     * <ul>
     *   <li>{@code deadline} 用于前端本地倒计时（不用每秒问服务端）</li>
     *   <li>{@code remainingSeconds} 用于前端校正 —— 万一用户改了系统时间，
     *       或者页面开了很久才刷新，本地倒计时就飘了，用服务端给的权威值覆盖</li>
     * </ul>
     * 前端可以每秒把本地倒计时减一，同时每隔几十秒调一次本接口，
     * 用 {@code remainingSeconds} 把本地计时拉回正轨。
     */
    @Schema(description = "交卷截止时刻")
    private LocalDateTime deadline;

    @Schema(description = "剩余秒数。以服务端为准，不依赖客户端计时")
    private Long remainingSeconds;

    @Schema(description = "考试状态", example = "ONGOING")
    private String status;

    @Schema(description = "题目列表（不含答案），按题号升序")
    private List<ExamQuestionVO> questions;

    /**
     * 已作答的内容：题目 ID -> 考生答案。
     * <p>
     * 用 Map 而不是 List，是因为它本质上是"按题目 ID 索引的查询表"。
     * 前端渲染第 N 题时可以直接 {@code savedAnswers[questionId]} 取值，
     * 不用遍历数组去找 —— 那是 O(n) 的查找，题目多时每次渲染都遍历一遍很浪费。
     */
    @Schema(description = "已作答内容，键为题目 ID")
    private java.util.Map<Long, String> savedAnswers;
}
