package com.exam.service;

import java.util.Map;
import java.util.Optional;

/**
 * 考试作答态的 Redis 存储。
 * <p>
 * 所有方法都不抛异常：Redis 只是加速层，DB 里还有一份（定时兜底同步），
 * 抖动时降级走库，只是慢一点，不能让考生答不了题。
 */
public interface ExamSessionService {

    /** 开考时初始化并定下 TTL。不初始化也能存，但"开了考一题没答"的 key 会永不过期。 */
    void initSession(Long examRecordId, long ttlSeconds);

    /** 单题落 Redis。返回 false 表示 Redis 不可用，调用方得自己写库，否则这题答案就丢了。 */
    boolean saveAnswer(Long examRecordId, Long questionId, String userAnswer);

    /** 读整场作答。empty = Redis 帮不上忙（改查库）；of(空 Map) = 确实一题没答。混了会把"Redis 挂了"当成"考生交白卷"。 */
    Optional<Map<Long, String>> getAnswers(Long examRecordId);

    /** 交卷成功后调用：主动删而不是等 TTL，Redis 是 noeviction，不清就一直占着内存。 */
    void clearSession(Long examRecordId);

    /** 尝试获取交卷锁，成功返回持有凭证（UUID）。只削峰 —— 正确性靠 DB 条件更新，锁没了最坏也是并发打库。 */
    Optional<String> tryLockSubmit(Long examRecordId);

    /** 释放锁，凭证校验后再删（Lua 原子），否则会把别人刚拿到的锁删掉。 */
    void unlockSubmit(Long examRecordId, String token);
}
