package com.exam.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 时间格式统一配置。
 * <p>
 * <b>为什么 application.yml 里的 {@code spring.jackson.date-format} 不够用？</b>
 * <p>
 * 那个配置项只对老的 {@code java.util.Date} 生效。
 * Java 8 引入的 {@code LocalDateTime} 属于 JSR-310 类型，由 {@code JavaTimeModule} 处理，
 * <b>完全不受 date-format 影响</b> —— 默认就序列化成 ISO-8601 的 {@code 2026-09-15T20:18:46} 这种带 T 的形式，
 * 而且纳秒部分有多少位就输出多少位（刚插入的对象会带出 {@code .9497933} 这样的尾巴）。
 * <p>
 * 结果就是同一张表的同一列，直接新建返回的和从库里查出来的格式还不一样。
 * 所以这里显式注册 JSR-310 的序列化器，让整个项目的时间输出保持一致。
 *
 * <h3>格式约定</h3>
 * <ul>
 *   <li>序列化（出参）：统一用 {@code yyyy-MM-dd HH:mm:ss}，人类可读</li>
 *   <li>反序列化（入参）：<b>故意配成更宽松的 ISO 格式</b>，能同时接受
 *       {@code 2026-09-15T20:18:46} 和 {@code 2026-09-15 20:18:46} 两种写法。
 *       前端传什么格式不该由后端单方面规定，容错一点更实用</li>
 * </ul>
 *
 * <h3>关于 Long 精度问题：本项目【刻意不做】全局转换</h3>
 * <p>
 * 雪花算法生成的 ID 是 19 位 Long，
 * 而 JavaScript 的 Number 只能安全表示 2^53（约 16 位），
 * 直接返回会让前端拿到<b>末尾几位被悄悄改写</b>的 ID，而且是静默的，极难排查。
 * 通行做法是全局把 Long 序列化成字符串。
 * <p>
 * <b>但这里没有这么做，因为本项目不满足触发条件</b>：主键是数据库自增，
 * 值域远在安全范围内。而 {@code serializerByType(Long.class, ...)} 是<b>无差别</b>生效的 ——
 * 它会把分页结果里的 {@code total} / {@code pages} 和响应体里的 {@code timestamp}
 * 一起变成字符串。我实际试过，返回的 JSON 长这样：
 * <pre>
 *   {"total":"1","current":"1","size":"5","timestamp":"1789474795927"}
 * </pre>
 * 前端拿到 {@code size} 想算页码还得再转一次类型，纯属自找麻烦。
 * <p>
 * 所以结论是：<b>不要为了一个当前不存在的问题，给所有接口加上后遗症</b>。
 * 真到了要换雪花 ID 那天，正确的做法也不是全局开关，
 * 而是在 ID 字段上单独标注 {@code @JsonSerialize(using = ToStringSerializer.class)} ——
 * 精确到字段，不误伤其他 Long。
 * <p>
 * 判断一项"大家都这么做"的配置要不要跟，标准不是它流不流行，
 * 而是它解决的问题在这个项目里存不存在。
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String TIME_PATTERN = "HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

        return builder -> builder
                // ---------- 序列化：出参统一可读格式 ----------
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter))
                .serializerByType(LocalDate.class,
                        new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_PATTERN)))
                .serializerByType(LocalTime.class,
                        new LocalTimeSerializer(DateTimeFormatter.ofPattern(TIME_PATTERN)))
                // 刻意【不】注册 Long -> String 的全局序列化器，原因见类注释

                // ---------- 反序列化：入参放宽，接受 ISO 和空格两种分隔符 ----------
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd['T'][' ']HH:mm[:ss]")))
                .deserializerByType(LocalDate.class,
                        new LocalDateDeserializer(DateTimeFormatter.ofPattern(DATE_PATTERN)))
                .deserializerByType(LocalTime.class,
                        new LocalTimeDeserializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));
    }
}
