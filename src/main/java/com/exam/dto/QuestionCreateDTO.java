package com.exam.dto;

import com.exam.entity.QuestionOption;
import com.exam.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 录入题目的请求参数。
 *
 * <h3>关于校验的边界：注解只能管"格式"，管不了"业务"</h3>
 * <p>
 * 下面的 {@code @NotNull}、{@code @Size} 这些注解，能保证字段**存在且格式合法**，
 * 但它们表达不了这样的规则：
 * <ul>
 *   <li>单选题的答案必须正好是某一个选项的 key</li>
 *   <li>多选题的答案至少要有两个选项，否则它就该是单选题</li>
 *   <li>简答题不该有选项，选择题必须有选项</li>
 *   <li>答案里不能出现选项列表中不存在的 key</li>
 * </ul>
 * <p>
 * 这些是<b>跨字段的业务规则</b>，注解做不到（或者勉强能做到但会变成
 * 一个难以维护的自定义校验器）。所以它们放在
 * {@code QuestionServiceImpl.validateByType()} 里手写。
 * <p>
 * 分清「格式校验」和「业务校验」的边界，是一个很实用的判断力 ——
 * 把业务规则硬塞进注解，最后往往写出一个谁也不敢碰的怪物。
 */
@Data
@Schema(description = "录入题目请求")
public class QuestionCreateDTO {

    @Schema(description = "题干", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "下列关于抽象类的说法，正确的是？")
    @NotBlank(message = "题干不能为空")
    @Size(max = 1000, message = "题干不能超过 1000 字")
    private String content;

    @Schema(description = "题型", requiredMode = Schema.RequiredMode.REQUIRED, example = "SINGLE")
    @NotNull(message = "题型不能为空")
    private QuestionType type;

    @Schema(description = "选项列表。选择题必填，简答题留空")
    private List<QuestionOption> options;

    @Schema(description = "标准答案。单选填 \"A\"，多选填 \"A,B,D\"，判断填 \"对\"/\"错\"，简答填参考答案",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "A")
    @NotBlank(message = "答案不能为空")
    @Size(max = 500, message = "答案不能超过 500 字")
    private String answer;

    @Schema(description = "建议分值", example = "5")
    @NotNull(message = "分值不能为空")
    @Min(value = 1, message = "分值至少为 1")
    @Max(value = 100, message = "单题分值不能超过 100")
    private Integer score;

    @Schema(description = "难度：1 易，2 中，3 难", example = "2")
    @NotNull(message = "难度不能为空")
    @Min(value = 1, message = "难度只能是 1/2/3")
    @Max(value = 3, message = "难度只能是 1/2/3")
    private Integer difficulty;
}
