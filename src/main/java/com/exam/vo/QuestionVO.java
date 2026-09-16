package com.exam.vo;

import com.exam.entity.QuestionOption;
import com.exam.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目详情，含标准答案，供教师管理题库用。学生答题接口不要用这个类。
 */
@Data
@Schema(description = "题目详情（含答案，仅供教师使用）")
public class QuestionVO {

    @Schema(description = "题目 ID")
    private Long id;

    @Schema(description = "题干")
    private String content;

    @Schema(description = "题型")
    private QuestionType type;

    @Schema(description = "题型中文名", example = "单选题")
    private String typeLabel;

    @Schema(description = "选项列表")
    private List<QuestionOption> options;

    @Schema(description = "标准答案")
    private String answer;

    @Schema(description = "建议分值")
    private Integer score;

    @Schema(description = "难度：1 易，2 中，3 难")
    private Integer difficulty;

    @Schema(description = "难度中文名", example = "中")
    private String difficultyLabel;

    @Schema(description = "出题教师 ID")
    private Long creatorId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
