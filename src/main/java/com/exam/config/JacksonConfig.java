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
 * Jackson 时间格式配置。
 * <p>
 * spring.jackson.date-format 只对 java.util.Date 生效，LocalDateTime 走 JavaTimeModule，
 * 默认输出 2026-09-15T20:18:46.9497933 这种带 T 还拖着纳秒尾巴的形式，所以这里显式注册
 * 序列化器：出参统一 yyyy-MM-dd HH:mm:ss，入参的 pattern 放宽，T 和空格两种分隔符都收。
 * <p>
 * 刻意不把 Long 全局转成 String —— 雪花 ID 才有 JS 精度问题，本项目主键是数据库自增，
 * 值域远在 2^53 以内。而 serializerByType(Long.class, ...) 是无差别生效的，会把分页的
 * total / pages 和响应体的 timestamp 一起变成字符串。真换了雪花 ID，应该在具体字段上标
 * {@code @JsonSerialize(using = ToStringSerializer.class)}。
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
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter))
                .serializerByType(LocalDate.class,
                        new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_PATTERN)))
                .serializerByType(LocalTime.class,
                        new LocalTimeSerializer(DateTimeFormatter.ofPattern(TIME_PATTERN)))
                // 不注册 Long -> String 的全局序列化器，原因见类注释

                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd['T'][' ']HH:mm[:ss]")))
                .deserializerByType(LocalDate.class,
                        new LocalDateDeserializer(DateTimeFormatter.ofPattern(DATE_PATTERN)))
                .deserializerByType(LocalTime.class,
                        new LocalTimeDeserializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));
    }
}
