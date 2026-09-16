package com.exam.vo;

import com.exam.entity.QuestionOption;
import com.exam.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 考试中的题目 —— <b>不含标准答案</b>。
 *
 * <h3>⚠️ 这个类存在的唯一理由就是"没有 answer 字段"</h3>
 * <p>
 * 项目里已经有一个 {@link QuestionVO}，字段几乎完全一样。之所以还要再写一个，
 * 是因为那个类带着 {@code answer}。
 * <p>
 * 考试进行中把答案返回给学生的后果很直接：
 * 学生按 F12 打开开发者工具，在 Network 面板里就能看到响应体里的正确答案，
 * 这场考试就废了。<b>而且服务端不会留下任何异常痕迹</b> ——
 * 接口返回 200、日志一切正常、界面显示也对。
 * <p>
 * 这类问题的可怕之处在于它<b>是"正常的"</b>：代码能跑、测试能过、
 * 演示时看不出任何毛病。它只在有人真正去翻响应体的时候才暴露。
 *
 * <h3>为什么不在 QuestionVO 上加个开关</h3>
 * <p>
 * 我见过这样的写法：给 VO 的 answer 字段加 {@code @JsonView} 或者
 * 一个 {@code boolean includeAnswer} 参数，靠调用方决定要不要序列化。
 * 这种设计的问题在于 —— <b>默认值是"包含"</b>。
 * 新来的接口只要忘了显式关掉，答案就漏出去了，
 * 而且没有任何机制会提醒你。
 * <p>
 * 分成两个类之后，编译器的类型系统就在帮你：
 * 考试接口的方法签名写着返回 {@code ExamQuestionVO}，
 * 你想传 {@code QuestionVO} 都传不进去。安全性由类型保证，
 * 而不是靠人记得关开关。<b>这是"让错误无法被表达"的思路。</b>
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

    /** 本题在本试卷中的分值 —— 来自 paper_question，不是题库里的建议分值 */
    @Schema(description = "本题分值")
    private Integer score;

    // 注意：这里没有 answer 字段。不要加。
    //
    // 如果将来确实需要"考完试后能看答案"的功能，
    // 正确的做法是在【成绩单】VO 里加，并且要判断该场考试是否已经结束 ——
    // 详见 ExamResultVO 的说明。
}
