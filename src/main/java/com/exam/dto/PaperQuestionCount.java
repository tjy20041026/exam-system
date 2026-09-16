package com.exam.dto;

import lombok.Data;

/**
 * 聚合查询的返回载体：某张试卷有多少道题。
 *
 * <h3>为什么不用 Map&lt;String, Object&gt; 接结果</h3>
 * <p>
 * MyBatis 允许把查询结果接到 {@code Map} 上，写起来确实省事。
 * 但那样就丢掉了<b>类型信息</b>：拿到的值得强转，
 * 列名拼错了要到运行时才发现，IDE 也没法帮你补全。
 * <p>
 * 用一个只有两个字段的小类，成本是几行代码，
 * 换来的编译期检查和代码可读性是划算的。
 *
 * <h3>为什么字段名和 SQL 里的别名必须严格对应</h3>
 * <p>
 * MyBatis 靠<b>列名（或别名）和属性名的匹配</b>来赋值。
 * 数据库列名是下划线风格 {@code paper_id}，开了
 * {@code map-underscore-to-camel-case} 后会映射到 {@code paperId}，
 * 所以 SQL 里写了 {@code AS paperId} 也行、不写别名也行。
 * <p>
 * 但 {@code cnt} 这种缩写列名没有下划线可转换，必须靠 {@code AS cnt} 显式指定别名，
 * 否则 MyBatis 找不到同名属性，赋值会被静默跳过 —— 结果是查出来一堆 null，
 * 日志里一句报错都没有。
 */
@Data
public class PaperQuestionCount {

    /** 试卷 ID */
    private Long paperId;

    /** 该试卷的题目数量 */
    private Integer questionCount;
}
