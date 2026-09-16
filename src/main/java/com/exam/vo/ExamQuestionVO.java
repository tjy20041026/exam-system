package com.exam.vo;

import com.exam.entity.QuestionOption;
import com.exam.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 考试中的题目，不含标准答案。不复用 QuestionVO 是因为它带 answer，泄题在类型层面就表达不出来。
 */
@Data
@Schema(description = "考试中的题目（不含答案）")
public class ExamQuestionVO {

    @Schema(description = "题目 ID")
    private Long questionId;

    @Schema(description = "题号（在本试卷中的顺序）")
    private Integer sortOrder;

    @Schema(description = "题干")
    private String content;

    @Schema(description = "题型")
    private QuestionType type;

    @Schema(description = "题型中文名", example = "单选题")
    private String typeLabel;

    @Schema(description = "选项列表。简答题为 null")
    private List<QuestionOption> options;

    @Schema(description = "本题分值")
    private Integer score;
}
