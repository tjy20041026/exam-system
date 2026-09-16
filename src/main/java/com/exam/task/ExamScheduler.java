package com.exam.task;

import com.exam.service.ExamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 考试相关的定时任务。两个任务各管一段：超时自动交卷管已经过了 deadline 的，
 * 兜底落库管还没到的，边界不重叠。业务逻辑都在 ExamService，这里只管调度和兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamScheduler {

    private final ExamService examService;

    /** 单批处理上限。写死在代码里的话调整要改代码重新发版，所以放配置。 */
    @Value("${exam.scheduler.batch-size:200}")
    private int batchSize;

    /**
     * 扫描超时未交卷的考试并自动交卷。学生断网、关掉页面跑掉的都有，没人交卷这些记录会一直挂在 ONGOING。
     * 用 fixedDelay 不用 fixedRate：前者是上一次执行结束后再等 N 毫秒，任务不会重叠执行。
     */
    @Scheduled(fixedDelayString = "${exam.scheduler.timeout-scan-interval-ms:30000}",
               initialDelayString = "${exam.scheduler.initial-delay-ms:20000}")
    public void autoSubmitOverdueExams() {
        List<Long> overdueIds;
        try {
            overdueIds = examService.findOverdueExamRecordIds(batchSize);
        } catch (Exception e) {
            // 这一轮直接放弃。不往外抛：抛出去堆栈看着像调度器坏了，实际只是这次查询失败
            log.error("扫描超时考试失败，本轮跳过", e);
            return;
        }

        if (overdueIds.isEmpty()) {
            // 30 秒跑一次，没数据就不打日志，否则一天 2880 条噪音
            return;
        }

        log.info("发现 {} 场超时未交卷的考试，开始自动交卷", overdueIds.size());

        int success = 0;
        for (Long id : overdueIds) {
            // 逐条隔离异常。一条脏数据抛出去，后面的记录这一轮全处理不到，下一轮还会断在同一处
            try {
                examService.autoSubmit(id);
                success++;
            } catch (Exception e) {
                log.error("自动交卷失败: examRecordId={}", id, e);
            }
        }
        log.info("自动交卷完成: 成功 {}/{}", success, overdueIds.size());
    }

    /**
     * 把进行中考试的 Redis 作答态定期同步到数据库。Redis 重启、断电、误 FLUSHDB 都会丢作答态，
     * 降级期间（Redis 不可用）写的答案更是压根不在 Redis 里。没有这个任务，出一次事
     * 就是所有正在考试的答案归零；60 秒的间隔，最坏丢掉最后一分钟的作答量。
     */
    @Scheduled(fixedDelayString = "${exam.scheduler.flush-interval-ms:60000}",
               initialDelayString = "${exam.scheduler.initial-delay-ms:30000}")
    public void flushSessionsToDatabase() {
        try {
            int flushed = examService.flushOngoingSessions(batchSize);
            if (flushed > 0) {
                // flushed=0 不打日志，这条只在真有数据被同步时出现
                log.info("兜底落库完成: 本次同步 {} 场进行中的考试", flushed);
            }
        } catch (Exception e) {
            // 同上，异常留在本轮内，别影响后续调度
            log.error("兜底落库任务异常，本轮跳过", e);
        }
    }
}
