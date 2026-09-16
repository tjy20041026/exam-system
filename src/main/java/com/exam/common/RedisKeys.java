package com.exam.common;

/**
 * Redis key 的集中定义。
 *
 * <h3>为什么不直接在业务代码里拼字符串</h3>
 * <p>
 * 因为 key 是<b>跨模块的契约</b>：写入在 {@code ExamService}，
 * 兜底落库在 {@code ExamScheduler}，清理在交卷流程里，
 * 调试时还要用 {@code redis-cli} 手工敲。
 * <p>
 * 如果各处各拼各的，会出两类问题：
 * <ul>
 *   <li><b>拼错一个字符</b> —— 写入用 {@code exam:session:1}、
 *       读取用 {@code exam:sessions:1}。不报错，只是永远读不到，
 *       表现为"断点续考失效"，排查起来毫无头绪</li>
 *   <li><b>改格式时漏改一处</b> —— 比如想把分隔符从 {@code :} 换成 {@code /}，
 *       散落的字符串常量里必定有漏网的，而漏掉的那个只在特定路径下才被触发</li>
 * </ul>
 * 集中定义之后，改格式只改这一个文件，编译器还会帮你找出所有引用点。
 *
 * <h3>命名规范：{@code 业务:子业务:标识}</h3>
 * <p>
 * 用冒号分层是 Redis 社区的通用约定（Redis Desktop Manager、
 * 各种监控工具都按冒号自动折叠成树形结构），
 * 好处是在 redis-cli 里 {@code KEYS exam:*} 能一眼看出这一片是谁的数据。
 * <p>
 * 另一个容易被忽略的好处：<b>多项目共用一个 Redis 实例时不会撞名</b>。
 * 本项目独占 6380 端口，所以前缀用的是 {@code exam}；
 * 如果将来要和其他系统合用一个实例，把前缀改成 {@code exam-system} 即可 ——
 * 又是一个"只改一处"的收益。
 */
public final class RedisKeys {

    /**
     * 工具类，禁止实例化。
     * <p>
     * 私有构造 + final 类，是为了让"这是一个纯常量的容器"这个意图
     * 在语言层面就表达出来 —— 而不是靠一句注释提醒别人别 new 它。
     */
    private RedisKeys() {
    }

    // ==================================================================
    //  考试作答态
    // ==================================================================

    /**
     * 考试作答态。类型：<b>Hash</b>。
     * <p>
     * field = 题目 ID，value = 考生答案（纯字符串，如 {@code "A"}、{@code "A,C"}）。
     * <p>
     * <b>为什么是 Hash 而不是 String</b>：
     * <ul>
     *   <li>学生每答一题只需 {@code HSET key questionId answer}，
     *       一条指令、O(1)。用 String 存整个答案集合的话，
     *       每次都要「读出 JSON → 反序列化 → 改一个字段 → 序列化 → 整体写回」，
     *       网络往返和 CPU 都翻好几倍</li>
     *   <li>可以只读某一题的答案（{@code HGET}），不用把整份答卷拉回来</li>
     *   <li>Hash 的单个 field 可以独立设置，天然适配"边答边存"的语义</li>
     * </ul>
     * <p>
     * 完整的 key 形如 {@code exam:session:12} —— 最后一段是 examRecordId。
     * <p>
     * <b>用 examRecordId 而不是 (paperId + userId) 做后缀</b>，
     * 是因为前者是数据库主键，唯一且短。<b>key 应该尽量短</b> ——
     * Redis 的 key 常驻内存，几百万个 key 时，每个多 20 字节就是几十 MB 的纯浪费。
     */
    public static String examSession(Long examRecordId) {
        return "exam:session:" + examRecordId;
    }

    /**
     * 交卷锁。类型：<b>String</b>，值为持有者的 UUID。
     * <p>
     * 用 {@code SET key uuid NX EX 30} 获取，用 Lua 脚本校验 UUID 后释放。
     * <p>
     * <b>注意这只是性能优化，不是正确性保证</b> ——
     * 真正的防重复交卷由 {@code exam_record} 的条件更新
     * （{@code WHERE id=? AND status='ONGOING'}）兜底。
     * 详见 {@code ExamSessionServiceImpl#tryLockSubmit}。
     */
    public static String submitLock(Long examRecordId) {
        return "exam:submit:lock:" + examRecordId;
    }

    // ==================================================================
    //  说明：这里【没有】exam:session:{id}:meta
    // ==================================================================
    //
    // 最初的设计里有这么一个 key，存 deadline / paperId / userId。
    // 实现时去掉了，理由如下：
    //
    //   这些字段在 exam_record 表里，一次主键查询就能拿到，代价极低。
    //   把它们复制进 Redis，等于【同一份数据存两处】——
    //   而只要有两处，就存在不一致的可能（Redis 重启、key 被淘汰、
    //   有人手工改库……），并且这种不一致是静默的：程序照常运行，
    //   只是用了过期的时间算倒计时。
    //
    // 换来的收益呢？把一次主键查询换成一次 Redis 查询 ——
    // 两者都是毫秒级，在考试场景（一个学生几分钟才发一次请求）下毫无差别。
    //
    // 结论：缓存的收益必须大于"引入第二份数据"的代价。
    //   收益接近零、代价是永久性的不一致风险 —— 这种缓存不该加。
    //
    // 真正值得放 Redis 的是【作答态】：
    //   它写入频率高（每次答题）、是本场考试独有的临时数据、
    //   且丢失可容忍（有定时兜底落库）。这才是 Redis 的主场。
}
