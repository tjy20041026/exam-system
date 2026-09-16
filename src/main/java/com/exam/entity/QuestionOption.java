package com.exam.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 一个选项，比如 {@code {"key":"A","value":"抽象类不能实例化"}}。
 *
 * <h3>为什么不用 Map&lt;String, String&gt;</h3>
 * <p>
 * {@code Map} 能表达同样的数据，但有两个问题：
 * <ul>
 *   <li><b>顺序不可靠</b>。JSON 对象在语义上无序，虽然实践中大多数库会保持插入顺序，
 *       但这是实现细节不是保证。选项顺序对考试来说是刚性要求 ——
 *       A 必须在 B 前面，不然学生答题时会以为界面出错了</li>
 *   <li><b>没有契约</b>。{@code Map} 的键名靠约定，
 *       调用方得靠猜或翻文档才知道是 {@code key} 还是 {@code optionKey}</li>
 * </ul>
 * <p>
 * 而 {@code List<QuestionOption>} 天然有序，字段名由编译器保证。
 *
 * <h3>什么时候该用 record</h3>
 * <p>
 * 这个类没有行为、没有可变状态、只是承载两个值的载体 ——
 * 正是 record 的适用场景。用 record 一行就够了，
 * 换成普通类要写 getter、equals、hashCode、toString 一大串。
 */
@Schema(description = "题目选项")
public record QuestionOption(

        @Schema(description = "选项标识，通常是 A/B/C/D", example = "A")
        String key,

        @Schema(description = "选项内容", example = "抽象类不能实例化")
        String value

) {
    /**
     * 给 record 加个便捷方法是可以的，record 并不禁止你写方法。
     * <p>
     * 但不能加实例字段 —— 这是 record 的基本约束，
     * 编译期就会拦住。不变性正是它安全的原因。
     */
    public boolean hasContent() {
        return key != null && !key.isBlank() && value != null && !value.isBlank();
    }
}
