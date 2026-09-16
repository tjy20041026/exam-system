package com.exam.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 序列化配置。
 * <p>
 * <b>这是新手最容易踩、且现象最迷惑的坑之一。</b>
 * Spring Boot 自动装配的 RedisTemplate 默认用 JdkSerializationRedisSerializer，
 * 后果是：
 * <ul>
 *   <li>用 redis-cli 去看，key 和 value 全是乱码（形如 {@code \xac\xed\x00\x05t\x00...}）</li>
 *   <li>调试时完全看不出存了什么，只能靠猜</li>
 *   <li>Java 对象序列化后体积远大于 JSON</li>
 *   <li>类结构变更后反序列化会直接失败（serialVersionUID 不匹配）</li>
 * </ul>
 * 所以必须改成：<b>key 用字符串，value 用 JSON</b>。
 */
@Configuration
public class RedisConfig {

    /**
     * 通用的 RedisTemplate：key 为 String，value 为任意对象（存为 JSON）。
     * <p>
     * 适合存对象，比如把某个 VO 缓存起来。
     * <p>
     * <b>但考试作答态不用它</b> —— 那里应该用 Spring Boot 自带的
     * {@code StringRedisTemplate}。理由见下方注释。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // key 用纯字符串序列化，这样 redis-cli 里能直接看懂
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        // value 用 JSON 序列化
        ObjectMapper mapper = new ObjectMapper();
        // 允许序列化 private 字段（默认只认 getter，会导致没有 getter 的字段丢失）
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 支持 LocalDateTime 等 Java 8 时间类型，不加会抛 InvalidDefinitionException
        mapper.registerModule(new JavaTimeModule());
        // 写入类型信息，反序列化时才能还原成原本的类而不是 LinkedHashMap。
        // LaissezFaireSubTypeValidator 不做白名单校验 —— 生产环境若 Redis 可被外部写入，
        // 应改用 BasicPolymorphicTypeValidator 限制可反序列化的类，否则存在反序列化攻击风险。
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);

        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(mapper);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /*
     * ============================================================
     * 关于「考试作答态」为什么不用上面的 RedisTemplate
     * ============================================================
     *
     * 作答态的结构是 Hash：field = 题目 ID，value = 答案字符串。
     * 两者本来就是字符串，用 StringRedisTemplate 直接存最合适：
     *
     *   1. 零序列化开销。答案就是个 "A" 或 "A,C"，包一层 JSON 再存毫无意义。
     *   2. redis-cli 里可直接读写，调试期能手工造数据、手工改数据。
     *      这对验证断点续考非常重要 —— 可以手动 HSET 几道题的答案，
     *      然后调恢复接口看能不能取回来，不用先跑一遍完整的考试流程。
     *   3. 避免上面的 DefaultTyping 带来的类型信息冗余
     *      （每个值都会多存一段 @class 字段）。
     *
     * StringRedisTemplate 由 Spring Boot 自动装配，无需在这里定义。
     * 使用方式见后续 ExamServiceImpl。
     */
}
