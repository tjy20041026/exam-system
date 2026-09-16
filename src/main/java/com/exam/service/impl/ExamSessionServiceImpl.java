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
 * 作答态 Redis 实现。用 StringRedisTemplate 而不是包一层 JSON 序列化：
 * 作答态就是 Hash&lt;题目ID, 答案字符串&gt;，两边都是字符串，JSON 只会多一层开销，
 * 而且没法和 redis-cli 手工交互 —— 调试时想手工 HSET 一道题验证断点续考都写不对格式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamSessionServiceImpl implements ExamSessionService {

    private final StringRedisTemplate redis;

    /** 交卷锁的持有时间：最坏情况下一个卡死的请求会占着锁 30 秒 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    /** 熔断到期时间戳（毫秒）。当前时间小于它，说明 Redis 还没恢复 */
    private final AtomicLong breakerUntil = new AtomicLong(0L);

    /** 熔断时长。太长恢复延迟明显，太短起不到保护作用，10 秒是折中 */
    private static final long BREAKER_MILLIS = 10_000L;

    /** 熔断是否打开。lettuce 超时 3000ms，Redis 挂了每个请求都白等 3 秒；
     * 失败一次后 10 秒内直接走数据库，不再发起连接 */
    private boolean breakerOpen() {
        return System.currentTimeMillis() < breakerUntil.get();
    }

    /** 上一次打熔断 ERROR 日志的时间。故障期间每个请求都会调 tripBreaker，做限流用 */
    private final AtomicLong lastBreakerLogAt = new AtomicLong(0L);

    /** 同一场故障中，两条 ERROR 日志的最小间隔 */
    private static final long BREAKER_LOG_INTERVAL_MILLIS = 60_000L;

    /**
     * Redis 操作失败时调用。日志限流到每分钟一条：不限流的话一场几分钟的故障
     * 能刷出几十万行 ERROR；只打第一条又会让人以为故障已经恢复。
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
        // 用 getAndSet：多个线程同时判定"之前是熔断状态"时，
        // 只有一个能拿到非 0 的旧值，日志不会重复打
        if (breakerUntil.getAndSet(0L) != 0L) {
            lastBreakerLogAt.set(0L);   // 恢复后重置，下次故障能立刻打出日志
            log.info("Redis 已恢复，关闭熔断器");
        }
    }

    @Override
    public void initSession(Long examRecordId, long ttlSeconds) {
        if (breakerOpen()) {
            return;
        }
        try {
            String key = RedisKeys.examSession(examRecordId);
            // 用 HSETNX 占位创建 key 并带上 TTL。不能用 SET key "" EX n ——
            // 那会把 key 变成 String 类型，之后 HSET 直接 WRONGTYPE。
            // 用 HSETNX 而不是 HSET 是为了让它幂等，重复开考不覆盖已有答案
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

            // 答案可能是 ""（主动清空）或 null（没答过）。Redis 的 Hash 存不了
            // null，统一转成空字符串 —— 判分时两者都算错
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
                    // field 不是合法数字：可能是别人用 redis-cli 手工写错了，
                    // 也可能是旧版本的数据格式不同。跳过 + 记日志，一条脏数据
                    // 不该让整场考试读不出来
                    log.warn("作答态里出现非法的题目 ID: examRecordId={}, field={}", examRecordId, field);
                }
            }
            // 这里是 Optional.of(空 Map) 而不是 empty —— Redis 明确回答了"没答过题"，
            // 和"Redis 帮不上忙"是两回事
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
            // 删不掉不是大事，TTL 到了会自动过期。交卷这时候已经成功了，
            // 不能因为清理缓存失败就告诉学生交卷失败
            tripBreaker("clearSession", e);
        }
    }

    @Override
    public Optional<String> tryLockSubmit(Long examRecordId) {
        if (breakerOpen()) {
            // 熔断期间放行而不是拒绝：锁只是性能优化，数据库才管正确性。
            // Redis 挂了就返回"获取锁失败"的话，等于让 Redis 的故障升级成
            // "所有人都交不了卷"。返回空凭证让调用方跳过释放锁，直接走条件更新
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
     * 释放锁的 Lua 脚本。必须原子执行：先 GET 再 DEL 的话，两步之间锁可能
     * 刚好到期被别人拿走，那一下 DEL 删的就是别人的锁 —— 窗口极窄但真的会发生。
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
            // 释放失败不影响交卷结果：锁有 30 秒 TTL，到点自动消失
            tripBreaker("unlockSubmit", e);
        }
    }

    /**
     * 判断某个异常是否属于"连不上 Redis"。目前没有调用点：连接失败该熔断，
     * 命令执行失败（如 WRONGTYPE）是代码 bug，熔断只会把问题藏起来。
     */
    @SuppressWarnings("unused")
    private boolean isConnectionFailure(Exception e) {
        return e instanceof RedisConnectionFailureException;
    }
}
