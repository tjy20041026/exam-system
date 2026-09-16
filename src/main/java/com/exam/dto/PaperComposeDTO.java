package com.exam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 组卷请求：指定这张试卷由哪些题目构成、每题多少分。
 *
 * <h3>语义是「全量替换」，不是「追加」</h3>
 * <p>
 * 调用一次组卷接口，这张试卷的题目列表就<b>完全变成</b>请求里的这一份。
 * 原来有、这次没传的题目会被移除。
 * <p>
 * 为什么不做成"增量添加"？因为增量语义下，
 * 调整试卷需要调用"加题"和"删题"两个接口，
 * 中间任何一步失败都会让试卷停在一个不完整的状态。
 * 而且前端要维护"哪些是这次新加的、哪些要删"，逻辑复杂且容易出错。
 * <p>
 * 全量替换则把一次组卷变成一个<b>幂等</b>操作：
 * 同样的请求调一次和调十次，结果完全一样。前端只要把当前编辑的完整列表提交上来即可。
 * <p>
 * 代价是数据量大时每次都要删了重建。但一张试卷的题目数量在几十条量级，
 * 这点开销完全可以接受 —— <b>用一点性能换掉一整类状态不一致的 bug，非常划算</b>。
 */
@Data
@Schema(description = "组卷请求")
public class PaperComposeDTO {

    @Schema(description = "题目列表", requiredMode = Schema.RequiredMode.REQUIRED)
    // @NotEmpty：至少选一道题。一个空试卷没有意义，
    // 而且如果允许空列表，这个接口就变成了"清空试卷"，容易误操作
    @NotEmpty(message = "试卷至少要有一道题")
    // @Valid 必须加在集合上，表示"要递归校验 List 里每个元素的注解"。
    // 不加的话，PaperQuestionItem 内部的 @NotNull 全部失效 ——
    // 因为 Spring 的校验默认只校验方法参数这一层，不会自动递归进集合元素。
    // 这是一个非常隐蔽的坑：注解都写对了，但完全不生效
    @Valid
    private List<PaperQuestionItem> questions;

    /**
     * 组卷中的单道题。
     * <p>
     * 做成静态内部类而不是独立的 DTO 文件，是因为它<b>只在组卷这个场景下有意义</b>，
     * 不会被别的地方复用。放在这里能让"它属于谁"一目了然，
     * 也避免 dto 包被一堆只出现一次的类塞满。
     */
    @Data
    @Schema(description = "组卷中的单道题")
    public static class PaperQuestionItem {

        @Schema(description = "题目 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "题目 ID 不能为空")
        private Long questionId;

        @Schema(description = "本题在本试卷中的分值", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
        @NotNull(message = "分值不能为空")
        @Min(value = 1, message = "单题分值至少为 1")
        @Max(value = 100, message = "单题分值不能超过 100")
        private Integer score;

        @Schema(description = "题目顺序，从 1 开始。不传则按列表顺序自动编号", example = "1")
        private Integer sortOrder;
    }
}
