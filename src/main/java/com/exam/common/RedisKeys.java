package com.exam.common;

/**
 * Redis key 集中定义。
 * <p>
 * key 是跨模块的契约：写入在 ExamService、兜底落库在 ExamScheduler、
 * 调试时还要用 redis-cli 手工敲。散在各处拼字符串，迟早出现
 * 「写的 session、读的 sessions」这种不报错但永远读不到的 bug。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 考试作答态，Hash。field = 题目 ID，value = 考生答案。用 Hash 是为了能单题 HSET / HGET。 */
    public static String examSession(Long examRecordId) {
        return "exam:session:" + examRecordId;
    }

    /** 交卷锁，值为持有者 UUID。SET NX EX 30 获取，Lua 校验后释放。只削峰，正确性靠 DB 条件更新兜底。 */
    public static String submitLock(Long examRecordId) {
        return "exam:submit:lock:" + examRecordId;
    }
}
