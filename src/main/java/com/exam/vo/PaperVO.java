package com.exam.vo;

import com.exam.enums.PaperStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 试卷概要，用于列表展示。
 * <p>
 * 刻意<b>不含</b>题目列表 —— 列表页一次可能返回几十张试卷，
 * 每张都拖着完整的题目数据，响应体会膨胀到几百 KB，
 * 而列表页根本用不到那些字段。
 * <p>
 * 这属于接口设计里的<b>按需返回</b>原则：列表接口给概要，详情接口给全量。
 * 代价是"想看某张试卷有哪些题"要多调一次详情接口 ——
 * 但只在用户真的点进去时才需要那一次调用。
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
