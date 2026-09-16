package com.exam.dto;

import com.exam.entity.QuestionOption;
import com.exam.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 修改题目的请求参数。
 * <p>
 * <b>为什么和 QuestionCreateDTO 分成两个类，而不是共用一个？</b>
 * <p>
 * 因为它们的约束<b>不一样</b>，而且差异是有意义的：
 * <ul>
 *   <li>创建时 {@code content}、{@code type}、{@code answer} 都是<b>必填</b>的
 *       （{@code @NotBlank}/{@code @NotNull}）</li>
 *   <li>修改时这些都<b>可以不传</b>，不传表示"这一项不动"。
 *       如果复用创建用的 DTO，那么每次改个分值，
 *       都得把题干、答案、选项全部原样再传一遍 ——
 *       传漏一个字段就把它清空了</li>
 * </ul>
 * <p>
 * 用一个类 + 在 Service 里写一堆 {@code if (dto.getContent() != null)}
 * 来区分"创建"和"更新"，最后会得到一个谁也不敢改的类。
 * 分成两个类虽然多写几十行，但每个类的意图是明确的。
 * <p>
 * <b>代价</b>：字段重复了一份。如果题目要加十个字段，
 * 两个类都得改。这是这类"显式优于隐式"选择的固有成本 ——
 * 在字段数量不多的情况下，可读性的收益大于重复的代价。
 */
@Data
@Schema(description = "修改题目请求，只传需要改的字段")
public class QuestionUpdateDTO {

    @Schema(description = "题干，不传表示不修改")
    @Size(max = 1000, message = "题干不能超过 1000 字")
    private String content;

    @Schema(description = "题型，不传表示不修改")
    private QuestionType type;

    @Schema(description = "选项列表，不传表示不修改。传空数组表示清空选项")
    private List<QuestionOption> options;

    @Schema(description = "标准答案，不传表示不修改")
    @Size(max = 500, message = "答案不能超过 500 字")
    private String answer;

    @Schema(description = "建议分值，不传表示不修改")
    @Min(value = 1, message = "分值至少为 1")
    @Max(value = 100, message = "单题分值不能超过 100")
    private Integer score;

    @Schema(description = "难度：1 易，2 中，3 难，不传表示不修改")
    @Min(value = 1, message = "难度只能是 1/2/3")
    @Max(value = 3, message = "难度只能是 1/2/3")
    private Integer difficulty;
}
