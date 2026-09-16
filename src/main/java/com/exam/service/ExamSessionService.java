package com.exam.service;

import java.util.Map;
import java.util.Optional;

/**
 * 考试作答态的 Redis 存储。
 *
 * <h3>这个接口存在的意义：把"缓存"和"业务"分开</h3>
 * <p>
 * {@code ExamService} 管的是业务规则（能不能考、怎么判分、谁能看）。
 * 本接口只管一件事：<b>作答内容放在 Redis 里怎么读写</b>。
 * <p>
 * 分开的好处很实际 —— 将来如果要把作答态换成 MongoDB、或者加一层本地缓存，
 * 改动只在这个接口的实现里，业务代码一行都不用动。
 *
 * <h3>⚠️ 本接口所有方法都【不抛异常】</h3>
 * <p>
 * 这是整个接口最重要的约定，也是最容易写错的地方。
 * <p>
 * 直觉上，Redis 连不上就该抛异常。但在这个业务里，Redis 是<b>加速层</b>而非唯一数据源：
 * 数据库里还有一份（由定时兜底落库同步）。如果 Redis 一抖动就抛异常，
 * 结果是<b>整个考试功能不可用</b> —— 本来只是"变慢一点"，变成了"考生答不了题"。
 * <p>
 * 所以这里的设计是：
 * <ul>
 *   <li>{@link #saveAnswer} 返回 {@code boolean} 表示"Redis 写成功了吗"，
 *       没成功时调用方直接写数据库，考生无感知</li>
 *   <li>{@link #getAnswers} 返回 {@code Optional}：
 *       <b>空的 Optional 表示"Redis 帮不上忙，去查数据库"</b>，
 *       而 {@code Optional.of(空Map)} 表示"Redis 说确实没答过题"</li>
 * </ul>
 * <p>
 * <b>这两种情况必须区分开</b>，否则会把"Redis 挂了"误判成"学生一题没答" ——
 * 那是直接清空考生答卷的严重事故。用 {@code Optional} 而不是返回 {@code null}，
 * 就是为了让调用方<b>在编译期</b>就被提醒：这里有个"取不到"的分支必须处理。
 */
public interface ExamSessionService {

    /**
     * 开考时初始化作答态（并设置过期时间）。
     * <p>
     * 其实不初始化也能用 —— {@code HSET} 会自动创建 key。
     * 单独提供这个方法是为了<b>先把 TTL 定下来</b>，
     * 避免出现"学生开了考但一题没答"时，这个 key 永不过期、一直占着内存。
     *
     * @param ttlSeconds 过期时间。传「剩余考试时长 + 缓冲」，
     *                   详见 {@code ExamSessionServiceImpl} 的说明
     */
    void initSession(Long examRecordId, long ttlSeconds);

    /**
     * 写入单题答案。
     *
     * @return {@code true} 表示已写入 Redis；
     *         {@code false} 表示 Redis 当前不可用，
     *         <b>调用方必须自己写数据库</b>，否则这道题的答案就丢了
     */
    boolean saveAnswer(Long examRecordId, Long questionId, String userAnswer);

    /**
     * 读取整场考试的作答内容。
     *
     * @return {@code Optional.empty()} —— Redis 不可用，请改查数据库；
     *         {@code Optional.of(map)} —— Redis 给的答案，
     *         其中 map 为空表示"确实一题都没答"
     */
    Optional<Map<Long, String>> getAnswers(Long examRecordId);

    /**
     * 删除作答态。交卷成功后调用。
     * <p>
     * 交卷时答案已经落库了，Redis 里这份就没用了。
     * 主动删掉而不是等 TTL 自然过期，是为了<b>尽快释放内存</b> ——
     * 一场考试结束后它还要占着"时长 + 30 分钟"，
     * 如果同时有几百场考试，这些"已经用完但还没过期"的数据
     * 会白白挤占内存。本项目实例是 {@code noeviction} 策略（内存满了写操作直接失败，
     * 而不是淘汰旧 key），所以这些数据不会被别人挤掉，但会把可用内存白白占着 ——
     * 一旦写操作开始失败，学生答题就会走降级路径去写数据库，
     * 等于 Redis 白部署了。该清的还是要及时清。
     */
    void clearSession(Long examRecordId);

    // ==================================================================
    //  交卷锁
    // ==================================================================

    /**
     * 尝试获取交卷锁。
     *
     * <h3>加这把锁的理由：不是为了正确，是为了别把数据库打爆</h3>
     * <p>
     * 防重复交卷的<b>正确性</b>由数据库的条件更新保证
     * （{@code UPDATE ... WHERE id=? AND status='ONGOING'}，靠行锁串行化）。
     * <b>这一层没有它也行</b> —— 去掉锁，50 个并发请求照样只会有一个成功，
     * 因为数据库不会骗人。
     * <p>
     * 那为什么还要加？因为那 49 个失败者<b>不是立刻失败的</b>：
     * 它们会全部涌进数据库，在那一行上排队等锁，直到赢家提交事务才发现条件不匹配。
     * 50 次数据库往返、50 个连接被占着、其中 49 个纯属白跑。
     * <p>
     * 有了这层 Redis 锁，49 个请求在 Redis 就返回了，数据库只承受 1 次。
     * <b>这是纯粹的流量削减，正确性仍然完全依赖数据库那一层。</b>
     *
     * <h3>既然数据库能保证正确性，为什么还要这一层分布式锁？</h3>
     * <p>
     * 反过来问更有意思 —— <b>既然加了分布式锁，为什么还要保留数据库条件更新？</b>
     * <p>
     * 因为分布式锁会失效：Redis 挂了、锁的 TTL 到了但业务还没跑完、
     * 网络分区导致客户端以为自己持锁实际没有。
     * <b>把正确性建立在锁上，等于把系统的正确性建立在一个可能失效的组件上。</b>
     * <p>
     * 正确的分层是：<b>锁负责性能，数据库负责正确性</b>。
     * 锁失效时最坏情况是"退化成没有优化的版本"，而不是"重复交卷"。
     * 性能优化永远不该以牺牲正确性为代价。
     *
     * @return 获取成功返回锁的持有凭证（UUID），失败返回 {@code Optional.empty()}。
     *         凭证要原样传给 {@link #unlockSubmit}，用于确认"删的是自己的锁"
     */
    Optional<String> tryLockSubmit(Long examRecordId);

    /**
     * 释放交卷锁。
     * <p>
     * 必须校验凭证后再删 —— 否则会出现"释放了别人的锁"：
     * <pre>
     *   A 拿到锁 → A 业务卡住超过 30 秒 → 锁自动过期
     *   → B 拿到锁 → A 业务结束，执行 DEL   ← 删掉了 B 的锁！
     *   → C 趁虚而入，此时 B 还在跑，两个请求同时在交卷
     * </pre>
     * 所以释放动作必须是「比对凭证 + 删除」的原子操作，
     * 用 Lua 脚本实现。详见实现类。
     */
    void unlockSubmit(Long examRecordId, String token);
}
