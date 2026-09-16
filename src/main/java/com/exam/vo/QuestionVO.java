package com.exam.vo;

import com.exam.entity.QuestionOption;
import com.exam.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目详情，<b>含答案</b>，供教师管理题库使用。
 *
 * <h3>⚠️ 这个 VO 绝不能在考试过程中返回给学生</h3>
 * <p>
 * 这里带着 {@code answer} 字段，是给出题教师看的 —— 他要核对答案对不对。
 * 但在学生答题的场景，同一个字段就是<b>事故</b>：
 * 学生只要打开浏览器的开发者工具看响应体，就能直接看到标准答案，
 * 这场考试就废了。
 * <p>
 * 所以 Day 5 实现考试流程时，必须另建一个不含 {@code answer} 的 VO
 * （比如 {@code ExamQuestionVO}），而不是图省事复用这个类。
 * <p>
 * 这类问题的危险之处在于：<b>它不会以任何方式表现出生病的症状</b>。
 * 接口正常返回、状态码 200、界面显示也完全正确 ——
 * 代码 review 时不特意去想"这个字段该不该给这个接口"就发现不了。
 * 防护手段是让类型的名字本身就带出警告，比如把类名叫作
 * {@code QuestionWithAnswerVO}，用它的时候自然会多想一秒。
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
