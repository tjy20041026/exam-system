package com.exam.task;

import com.exam.service.ExamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 考试相关的定时任务。
 *
 * <h3>两个任务，各管一段</h3>
 * <pre>
 *   超时自动交卷   每 30 秒   处理 status=ONGOING 且 deadline < now  的记录
 *   兜底落库       每 60 秒   处理 status=ONGOING 且 deadline >= now 的记录
 * </pre>
 * 边界不重叠（一个管已超时的，一个管未超时的），
 * 所以同一场考试在任一时刻只会被一个任务处理。
 *
 * <h3>本类只负责"什么时候跑"和"出错了怎么办"</h3>
 * <p>
 * 业务逻辑和数据库/Redis 访问全都在 {@link ExamService} 里。
 * 这么分的好处是：定时任务是最难写单元测试的东西（要控制时间），
 * 把逻辑挪出去之后，业务逻辑可以脱离调度框架单独测。
 * <p>
 * 本类剩下的职责只有两件事：
 * <ol>
 *   <li><b>批量的边界控制</b> —— 一次处理多少条、失败了怎么办</li>
 *   <li><b>异常隔离</b> —— 单条失败绝不能中断整批</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamScheduler {

    private final ExamService examService;

    /**
     * 单批处理上限。
     * <p>
     * 配置化的意义：不同规模下这个值该不一样。
     * 十个人的小测验，一次 100 条绰绰有余；
     * 上万人的统考，可能需要调大，或者把间隔缩短。
     * 把它写死在代码里，调整就得改代码重新发版。
     */
    @Value("${exam.scheduler.batch-size:200}")
    private int batchSize;

    // ==================================================================
    //  任务一：超时自动交卷
    // ==================================================================

    /**
     * 扫描超时未交卷的考试，自动交卷。
     *
     * <h3>为什么必须有这个任务</h3>
     * <p>
     * 因为<b>不能指望学生自己交卷</b>。真实的超时场景里，学生往往是：
     * <ul>
     *   <li>浏览器崩了 / 电脑没电了 / 网断了</li>
     *   <li>关掉页面跑了，压根不打算交</li>
     *   <li>就耗着，想看看能不能多答一会儿</li>
     * </ul>
     * 这几种情况下，卷子会永远挂在 {@code ONGOING}。
     * 后果不只是"看起来不整洁"：这场考试永远不出成绩，
     * 教师的成绩统计里永远缺一个人，而且这个状态会一直持续下去。
     * <p>
     * <b>注意：答案不会因此丢失。</b> 即使学生是在第 58 分钟被系统自动交卷的，
     * 他在第 57 分钟答的题也已经在 Redis 里（并且被兜底落库同步到数据库了），
     * 交卷流程会把这些答案全部读到并判分。
     * "超时"限制的是<b>继续作答</b>，不是"抹掉已答的内容"。
     *
     * <h3>为什么要用 initialDelay</h3>
     * <p>
     * {@code fixedDelay} 是"上一次执行结束后，再等 N 毫秒执行"——
     * 这一点很重要，它保证了任务不会重叠执行。
     * <p>
     * 如果用 {@code fixedRate}（按固定频率触发，不管上次跑完没有），
     * 当一次执行耗时超过间隔时，下一次会被立刻触发，
     * 于是两个实例同时扫描同一批数据。虽然我们的并发保护
     * （条件更新）能保证不会重复交卷，但会白白浪费资源，
     * 而且日志里会出现一堆"已被处理"的干扰信息。
     */
    @Scheduled(fixedDelayString = "${exam.scheduler.timeout-scan-interval-ms:30000}",
               initialDelayString = "${exam.scheduler.initial-delay-ms:20000}")
    public void autoSubmitOverdueExams() {
        List<Long> overdueIds;
        try {
            overdueIds = examService.findOverdueExamRecordIds(batchSize);
        } catch (Exception e) {
            // 连查询都失败了（比如数据库连不上），这一轮直接放弃。
            // 不往外抛是关键：@Scheduled 方法抛出异常后，
            // Spring 默认只是打条日志，任务本身还会继续按计划触发 ——
            // 但如果异常没被捕获，堆栈看起来会像是"调度器坏了"，
            // 而实际上只是这一轮的数据库查询失败。
            // 【捕获后明确说出"本轮放弃"，排查时一眼就能看懂。】
            log.error("扫描超时考试失败，本轮跳过", e);
            return;
        }

        if (overdueIds.isEmpty()) {
            // 注意这里【什么都不打】。
            //
            // 这个任务每 30 秒跑一次，一天 2880 次。
            // 如果每次都打一行"没有超时的考试"，日志里就全是噪音，
            // 真正的问题反而被埋掉了。
            //
            // 一条实用的原则：定时任务在"无事发生"时应该保持安静。
            return;
        }

        log.info("发现 {} 场超时未交卷的考试，开始自动交卷", overdueIds.size());

        int success = 0;
        for (Long id : overdueIds) {
            // 逐条隔离异常。
            //
            // 如果这里不 try-catch，第 37 条因为脏数据抛异常，
            // 后面 163 条就全都不处理了 —— 而这 163 个学生的成绩
            // 要等到下一轮（30 秒后）才有机会被处理。
            // 更糟的是：如果那条脏数据一直存在，每一轮都会在同一个位置断掉，
            // 后面的记录【永远】处理不到。这是批处理最经典的故障模式。
            try {
                examService.autoSubmit(id);
                success++;
            } catch (Exception e) {
                log.error("自动交卷失败: examRecordId={}", id, e);
            }
        }
        log.info("自动交卷完成: 成功 {}/{}", success, overdueIds.size());
    }

    // ==================================================================
    //  任务二：兜底落库
    // ==================================================================

    /**
     * 把进行中考试的 Redis 作答态定期同步到数据库。
     *
     * <h3>这是在给"Redis 会丢数据"买保险</h3>
     * <p>
     * Redis 是内存数据库，以下任何一件事都会让作答态消失或残缺：
     * <ul>
     *   <li><b>Redis 服务不可用期间产生的作答</b> —— 那段时间走的是降级路径，
     *       答案直接写进了数据库，Redis 里根本没有。<b>这是最常见的场景</b>，
     *       不需要任何灾难，一次网络抖动就够了</li>
     *   <li><b>机器断电 / 磁盘故障</b> —— 本项目开了 AOF（everysec），
     *       单纯的进程重启能恢复，但断电可能丢掉最后 1 秒的写入，
     *       磁盘损坏则可能整份 AOF 都读不出来</li>
     *   <li><b>有人误操作</b> —— 一条 {@code FLUSHDB} 就够了
     *       （本次开发过程中我就真的执行过一次，用来做干净测试）</li>
     * </ul>
     * <p>
     * <b>注意"内存被淘汰"不在这个列表里</b>：本项目实例配的是
     * {@code noeviction}，内存满了会让写操作失败而不是淘汰旧数据。
     * 这是刻意的取舍 —— 淘汰意味着<b>静默地丢掉某个考生的答案</b>，
     * 而写失败会走降级路径，学生至少还能继续答题。
     * 如果没有这个任务，上面任何一件事发生 = <b>所有正在考试的学生
     * 答案全部归零</b>。
     * <p>
     * 有了它，最坏情况变成"丢掉最后一次同步之后的几道题"。
     * 60 秒的间隔意味着：<b>最坏丢 1 分钟的作答量</b>。
     * <p>
     * 这个间隔就是那个经典的取舍点 ——
     * 调短（比如 5 秒）：数据更安全，但数据库压力成倍上升，
     * 而且 Redis 存在的意义（扛住高频写）被削弱了。
     * 调长（比如 10 分钟）：数据库轻松了，但一旦 Redis 出事，
     * 学生可能丢掉 10 分钟的作答。
     * <p>
     * 60 秒是一个基于"考试场景下学生一分钟能答几道题"的估算：
     * 一分钟最多也就答几道题，丢了也就是重答几道，可以接受。
     */
    @Scheduled(fixedDelayString = "${exam.scheduler.flush-interval-ms:60000}",
               initialDelayString = "${exam.scheduler.initial-delay-ms:30000}")
    public void flushSessionsToDatabase() {
        try {
            int flushed = examService.flushOngoingSessions(batchSize);
            if (flushed > 0) {
                // 同样保持安静：flushed=0 时不打日志。
                // 这条 INFO 只在【真的有数据被同步】时出现，
                // 所以看到它就说明"刚才有学生在答题"，是有信息量的。
                log.info("兜底落库完成: 本次同步 {} 场进行中的考试", flushed);
            }
        } catch (Exception e) {
            log.error("兜底落库任务异常，本轮跳过", e);
        }
    }
}
