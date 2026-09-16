package com.exam.vo;

import com.exam.entity.QuestionOption;
import com.exam.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 试卷中的一道题。score 是 paper_question.score（本试卷里的分值），sortOrder 是这张卷子里的题号。
 */
@Data
@Schema(description = "试卷中的题目")
public class PaperQuestionVO {

    @Schema(description = "题目 ID")
    private Long questionId;

    @Schema(description = "题号（在本试卷中的顺序）")
    private Integer sortOrder;

    @Schema(description = "题干")
    private String content;

    @Schema(description = "题型")
    private QuestionType type;

    @Schema(description = "题型中文名")
    private String typeLabel;

    @Schema(description = "选项列表")
    private List<QuestionOption> options;

    @Schema(description = "标准答案")
    private String answer;

    @Schema(description = "本题在本试卷中的分值")
    private Integer score;

    @Schema(description = "难度：1 易，2 中，3 难")
    private Integer difficulty;
}
