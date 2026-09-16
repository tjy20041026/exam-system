package com.exam.service.impl;

import com.exam.common.RedisKeys;
import com.exam.service.ExamSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 作答态 Redis 实现。
 *
 * <h3>为什么用 StringRedisTemplate 而不是自定义的 RedisTemplate</h3>
 * <p>
 * 作答态本身就是 Hash&lt;题目ID, 答案字符串&gt;，两边都是字符串，
 * 直接存最合适。用 JSON 序列化包一层没有任何好处，反而：
 * <ul>
 *   <li>多一层序列化开销</li>
 *   <li>每个值多一段 {@code @class} 类型信息</li>
 *   <li><b>没法和 redis-cli 手工交互</b> —— 调试时想手工 HSET 一道题的答案
 *       来验证断点续考，用 JSON 序列化就写不对格式，只能反序列化报错</li>
 * </ul>
 * {@link com.exam.config.RedisConfig} 里对此有更详细的说明。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamSessionServiceImpl implements ExamSessionService {

    private final StringRedisTemplate redis;

    /** 交卷锁的持有时间。最坏情况下，一个卡死的请求会阻塞交卷 30 秒 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    // ==================================================================
    //  轻量熔断器
    // ==================================================================

    /**
     * 熔断到期时间戳（毫秒）。当前时间小于它，就认为 Redis 还没恢复。
     */
    private final AtomicLong breakerUntil = new AtomicLong(0L);

    /**
     * 熔断时长。
     * <p>
     * 选 10 秒而不是 60 秒，是因为这个值决定了"Redis 恢复后多久才能重新用上"。
     * 太长会让恢复延迟变得明显，太短则起不到保护作用。
     * 10 秒是个折中：既能挡住故障期间的请求洪峰，恢复也能很快被感知。
     */
    private static final long BREAKER_MILLIS = 10_000L;

    /**
     * 熔断器是否处于打开状态。
     * <p>
     * <b>为什么需要这个东西：</b>lettuce 的连接超时配的是 3000ms。
     * Redis 挂掉时，如果每个请求都老老实实去尝试连接，
     * 每个请求都要白等 3 秒 —— 学生答一题卡 3 秒，整个考试体验直接崩掉。
     * <p>
     * 熔断器的作用就是：<b>已知对方挂了，就别再去敲那扇门。</b>
     * 失败一次之后，接下来 10 秒内直接走数据库，一次连接尝试都不发起。
     * <p>
     * 这是"断路器模式"的最小实现，只有 20 行，但解决的是真实的雪崩问题 ——
     * 没有它，一个依赖的故障会通过线程阻塞迅速传染给整个系统。
     */
    private boolean breakerOpen() {
        return System.currentTimeMillis() < breakerUntil.get();
    }

    /**
     * 上一次打出熔断 ERROR 日志的时间戳。
     * <p>
     * 用来做"日志限流"：故障期间每个请求都会调用 tripBreaker，
     * 如果每次都打日志，几秒钟就能刷出成千上万行，
     * 真正的错误信息反而被自己的噪音淹没了。
     */
    private final AtomicLong lastBreakerLogAt = new AtomicLong(0L);

    /** 同一场故障中，两次 ERROR 日志之间至少间隔这么久 */
    private static final long BREAKER_LOG_INTERVAL_MILLIS = 60_000L;

    /**
     * Redis 操作失败时调用，打开熔断器。
     *
     * <h3>日志为什么要限流到"每分钟一条"</h3>
     * <p>
     * 完全不限流：一个持续 5 分钟的 Redis 故障，如果有几百个学生同时答题，
     * 会产生几十万行 ERROR —— 磁盘被写满，日志文件大到打不开，
     * 排查时真正需要的那一行根本找不到。
     * <p>
     * 但反过来"整场故障只打第一条"也有问题：一条 ERROR 之后彻底静默，
     * 运维看到日志会以为故障已经恢复了。如果这期间恰好没有学生答题，
     * 就完全没有其他日志能提示"Redis 还挂着"。
     * <p>
     * 所以取中间值：<b>故障持续期间，每分钟提醒一次</b>。
     * 既不会淹没日志，也不会让人误以为已经恢复。
     */
    private void tripBreaker(String op, Exception e) {
        breakerUntil.set(System.currentTimeMillis() + BREAKER_MILLIS);

        long now = System.currentTimeMillis();
        long last = lastBreakerLogAt.get();
        if (now - last >= BREAKER_LOG_INTERVAL_MILLIS) {
            lastBreakerLogAt.set(now);
            log.error("Redis 操作失败，熔断 {} 秒后重试。操作={}, 原因={}",
                    BREAKER_MILLIS / 1000, op, e.getMessage());
        }
    }

    /** Redis 操作成功时调用，关闭熔断器 */
    private void resetBreaker() {
        // 用 getAndSet 而不是"先判断再设置"：
        // 这样即使多个线程同时判定"之前是熔断状态"，
        // 也只有一个线程能拿到非 0 的旧值，日志不会重复打。
        if (breakerUntil.getAndSet(0L) != 0L) {
            lastBreakerLogAt.set(0L);   // 恢复后重置，下次故障能立刻打出日志
            log.info("Redis 已恢复，关闭熔断器");
        }
    }

    // ==================================================================
    //  作答态读写
    // ==================================================================

    @Override
    public void initSession(Long examRecordId, long ttlSeconds) {
        if (breakerOpen()) {
            return;
        }
        try {
            String key = RedisKeys.examSession(examRecordId);
            // 用 HSETNX 占一个占位 field 来创建 key 并带上 TTL。
            //
            // 为什么不用 SET key "" EX n —— 那会把 key 变成 String 类型，
            // 之后 HSET 会直接报 WRONGTYPE 错误。这是 Redis 里很常见的一类事故：
            // 同一个 key 被两种数据结构用过。
            //
            // 为什么用 HSETNX 而不是 HSET —— HSETNX 只在 field 不存在时写入，
            // 所以重复开考不会覆盖已有答案。（虽然开考时本来也没答案，
            // 但用 HSETNX 让这个方法的语义变成"幂等初始化"，更安全。）
            Boolean created = redis.opsForHash().putIfAbsent(key, "__init__", "1");
            if (Boolean.TRUE.equals(created)) {
                redis.expire(key, Duration.ofSeconds(ttlSeconds));
                log.info("已创建考试会话: examRecordId={}, TTL={}秒", examRecordId, ttlSeconds);
            }
            resetBreaker();
        } catch (Exception e) {
            tripBreaker("initSession", e);
        }
    }

    @Override
    public boolean saveAnswer(Long examRecordId, Long questionId, String userAnswer) {
        if (breakerOpen()) {
            return false;
        }
        try {
            String key = RedisKeys.examSession(examRecordId);

            // 答案可能是 ""（学生主动清空）或 null（没答过）。
            // Redis 的 Hash 存不了 null，所以统一转成空字符串 ——
            // 反正判分时两者都算错，而"是否答过题"这个信息
            // 可以通过 field 是否存在来判断。
            redis.opsForHash().put(key, String.valueOf(questionId),
                    userAnswer == null ? "" : userAnswer);

            resetBreaker();
            return true;
        } catch (Exception e) {
            tripBreaker("saveAnswer", e);
            return false;
        }
    }

    @Override
    public Optional<Map<Long, String>> getAnswers(Long examRecordId) {
        if (breakerOpen()) {
            return Optional.empty();
        }
        try {
            Map<Object, Object> raw = redis.opsForHash().entries(RedisKeys.examSession(examRecordId));
            resetBreaker();

            Map<Long, String> result = new LinkedHashMap<>(raw.size());
            for (Map.Entry<Object, Object> e : raw.entrySet()) {
                String field = String.valueOf(e.getKey());
                // 跳过初始化时占位用的 field
                if ("__init__".equals(field)) {
                    continue;
                }
                try {
                    result.put(Long.valueOf(field), e.getValue() == null ? null : String.valueOf(e.getValue()));
                } catch (NumberFormatException ex) {
                    // field 不是合法数字。可能是有人用 redis-cli 手工写错了，
                    // 也可能是历史版本的数据格式不同。
                    // 【跳过 + 记日志】比抛异常好：一条脏数据不该让整场考试读不出来。
                    log.warn("作答态里出现非法的题目 ID: examRecordId={}, field={}", examRecordId, field);
                }
            }
            // 注意这里是 Optional.of(空 Map) 而不是 Optional.empty() ——
            // Redis 明确回答了"没答过题"，和"Redis 帮不上忙"是两回事。
            return Optional.of(result);
        } catch (Exception e) {
            tripBreaker("getAnswers", e);
            return Optional.empty();
        }
    }

    @Override
    public void clearSession(Long examRecordId) {
        if (breakerOpen()) {
            return;
        }
        try {
            redis.delete(RedisKeys.examSession(examRecordId));
            resetBreaker();
        } catch (Exception e) {
            // 删不掉不是大事：TTL 到了会自动过期。
            // 所以这里只记日志、不抛异常、不影响交卷流程 ——
            // 【交卷已经成功了，不能因为清理缓存失败就告诉学生交卷失败】。
            tripBreaker("clearSession", e);
        }
    }

    // ==================================================================
    //  交卷锁
    // ==================================================================

    @Override
    public Optional<String> tryLockSubmit(Long examRecordId) {
        if (breakerOpen()) {
            // 熔断期间【放行】而不是拒绝。
            //
            // 这一点很关键：锁只是性能优化，数据库才是正确性保证。
            // 如果 Redis 挂了就返回"获取锁失败"，等于让 Redis 的故障
            // 直接升级成"所有人都交不了卷" —— 那才是真正的事故。
            //
            // 返回一个空凭证，让调用方跳过"释放锁"这一步，
            // 直接走数据库的条件更新。正确性一点没丢。
            return Optional.of("__no_redis__");
        }
        try {
            String token = UUID.randomUUID().toString();
            Boolean ok = redis.opsForValue()
                    .setIfAbsent(RedisKeys.submitLock(examRecordId), token, LOCK_TTL);
            resetBreaker();

            if (Boolean.TRUE.equals(ok)) {
                return Optional.of(token);
            }
            return Optional.empty();
        } catch (Exception e) {
            tripBreaker("tryLockSubmit", e);
            // 同上：Redis 出问题时放行，不阻断交卷
            return Optional.of("__no_redis__");
        }
    }

    /**
     * 释放锁的 Lua 脚本。
     *
     * <h3>为什么必须是 Lua，不能先 GET 再 DEL</h3>
     * <p>
     * 先 GET 出来比对凭证、确认是自己的锁、再 DEL —— 看起来没问题，
     * 但 GET 和 DEL 之间有个时间窗口：
     * <pre>
     *   A 的锁过期了
     *   B 拿到锁（key 的值变成了 B 的 UUID）
     *   A 执行 GET → 读到的竟然还是自己的 UUID？
     *      ↑ 不可能，此时 key 已是 B 的值，A 会正确地不删
     *
     *   ——但换一个时序：
     *   A 执行 GET → 读到自己 UUID，判断"是我的锁"✓
     *   【A 的锁刚好在这一刻到期，key 被 Redis 自动删除】
     *   B 拿到锁，写入 B 的 UUID
     *   A 执行 DEL → 删掉了 B 的锁 ❌
     * </pre>
     * 这个窗口极窄（微秒级），但它<b>真的会发生</b>，
     * 而且只在系统繁忙、锁恰好过期的那一瞬间出现 ——
     * 属于典型的"压测跑一万次才复现一次、线上偶发、无法复现"的幽灵 bug。
     * <p>
     * Redis 执行 Lua 脚本时是<b>单线程原子执行</b>的，
     * 脚本运行期间不会插入其他命令，所以"比对 + 删除"能真正变成一个原子操作。
     * <p>
     * 返回 1 表示删除成功，0 表示锁已不属于自己（或已过期）。
     */
    private static final RedisScript<Long> UNLOCK_SCRIPT = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end",
            Long.class);

    @Override
    public void unlockSubmit(Long examRecordId, String token) {
        // 熔断期间拿到的假凭证，不需要释放
        if (token == null || "__no_redis__".equals(token) || breakerOpen()) {
            return;
        }
        try {
            Long deleted = redis.execute(UNLOCK_SCRIPT,
                    List.of(RedisKeys.submitLock(examRecordId)), token);
            if (deleted != null && deleted == 0L) {
                log.warn("交卷锁已不属于自己，可能已超时自动释放: examRecordId={}", examRecordId);
            }
            resetBreaker();
        } catch (Exception e) {
            // 释放失败不影响交卷结果：锁有 30 秒 TTL，到点会自动消失。
            // 这里只记日志。这是"宁可让锁多存在一会儿，也不能影响主流程"的取舍。
            tripBreaker("unlockSubmit", e);
        }
    }

    /**
     * 判断某个异常是否属于"连不上 Redis"。
     * <p>
     * 目前没有实际调用，保留它是为了说明一个区分：
     * <b>连接失败</b>（{@link RedisConnectionFailureException}）和
     * <b>命令执行失败</b>（如 WRONGTYPE）需要不同的应对策略 ——
     * 前者该熔断，后者是代码 bug，熔断只会把问题藏起来。
     * <p>
     * 当前实现对两者一视同仁（都熔断），因为在这个业务里
     * "Redis 不能用了"这个结论对两种情况都成立。
     * 如果将来要做更精细的治理，这里是区分点。
     */
    @SuppressWarnings("unused")
    private boolean isConnectionFailure(Exception e) {
        return e instanceof RedisConnectionFailureException;
    }
}
