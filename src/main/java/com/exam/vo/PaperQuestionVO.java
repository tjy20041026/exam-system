package com.exam.vo;

import com.exam.entity.QuestionOption;
import com.exam.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 试卷中的一道题 —— 是「试卷-题目」这个关系的展开视图，
 * 而不是单纯的题目。
 *
 * <h3>为什么需要这个类，不能直接用 QuestionVO</h3>
 * <p>
 * 两个原因：
 * <ol>
 *   <li><b>分值来源不同</b>。这里的 {@code score} 是 {@code paper_question.score}
 *       （本试卷中的分值），不是 {@code question.score}（题库里的建议分值）。
 *       两者经常不一样，用 QuestionVO 会让人分不清看到的是哪一个</li>
 *   <li><b>需要顺序</b>。{@code sortOrder} 是这张试卷里的题号，
 *       它不属于题目本身 —— 同一道题在 A 卷是第 3 题、在 B 卷可能是第 7 题</li>
 * </ol>
 * <p>
 * 把「关系」和「实体」的视图分开，是避免字段含义混淆的有效手段。
 * 混在一起用，接手的人（包括三个月后的你自己）就得靠猜。
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

    /** 本题在本试卷中的分值，来源是 paper_question.score */
    @Schema(description = "本题在本试卷中的分值")
    private Integer score;

    @Schema(description = "难度：1 易，2 中，3 难")
    private Integer difficulty;
}
