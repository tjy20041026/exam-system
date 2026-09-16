package com.exam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 保存单题答案的入参。
 *
 * <h3>为什么 userAnswer 上没有 @NotBlank</h3>
 * <p>
 * 因为<b>空字符串是合法输入</b>。
 * <p>
 * 学生点开一道题、写了两行、又全删掉，然后点"保存" ——
 * 这个动作的语义是"这题我清空了"，是一个真实且需要被记录的状态。
 * 如果加了 {@code @NotBlank}，这个请求会被打成 400 校验失败，
 * 学生的操作就静默丢失了。
 * <p>
 * <b>null 和空字符串在本系统里是两个不同的状态</b>：
 * <ul>
 *   <li>{@code null} —— 这道题从没被碰过（Redis Hash 里根本没这个 field）</li>
 *   <li>{@code ""} —— 学生主动清空了答案</li>
 * </ul>
 * 判分时两者都算错，但保留区别是为了学情分析。
 * <p>
 * 这里只校验 {@code questionId} 非空，因为它是定位依据，缺了就没法存。
 * 至于"这个 questionId 到底属不属于本场试卷"，那是业务校验，
 * 放在 Service 里查库判断，不用注解 —— <b>注解只能做格式校验，
 * 做不了需要查数据库的校验。</b>
 */
@Data
@Schema(description = "保存单题答案")
public class SaveAnswerDTO {

    @NotNull(message = "题目 ID 不能为空")
    @Schema(description = "题目 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long questionId;

    /**
     * 考生答案。不加 @NotBlank，理由见类注释。
     * <p>
     * 单选/判断传 "A"/"对"，多选传 "A,B,C"（<b>有序且去重</b>，
     * 由前端保证格式，服务端判分时会再归一化一次），简答题传原文。
     */
    @Schema(description = "考生答案。允许为空字符串（表示清空该题答案）", example = "A")
    private String userAnswer;
}
