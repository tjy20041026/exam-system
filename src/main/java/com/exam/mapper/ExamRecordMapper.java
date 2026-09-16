package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.entity.ExamRecord;
import com.exam.enums.ExamStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试记录 Mapper。
 */
@Mapper
public interface ExamRecordMapper extends BaseMapper<ExamRecord> {

    /**
     * 【核心】把考试置为已结束 —— 靠条件更新实现"只有一个请求能成功"。
     *
     * <h3>为什么这一条 UPDATE 就能防住并发交卷</h3>
     * <p>
     * 关键在于 {@code WHERE ... AND status = 'ONGOING'} 这个条件和
     * <b>数据库的行锁</b>共同作用：
     * <pre>
     *   时刻   请求 A                          请求 B
     *   ─────────────────────────────────────────────────────────
     *   t1     UPDATE ... WHERE status=ONGOING
     *          → 拿到行锁，改 status，affected=1
     *   t2                                     UPDATE ... WHERE status=ONGOING
     *                                          → 想拿行锁，但被 A 占着，【阻塞等待】
     *   t3     COMMIT（释放行锁）
     *   t4                                     → 拿到锁，重新读取该行
     *                                          → 此时 status 已是 SUBMITTED
     *                                          → WHERE 条件不匹配，affected=0
     * </pre>
     * <p>
     * B 拿到 {@code affected = 0}，就知道自己没抢到，直接返回"请勿重复提交"。
     * <b>整个过程不需要任何额外的锁机制</b> —— InnoDB 的行锁天然提供了互斥。
     *
     * <h3>为什么不用「先 SELECT 查状态，再 UPDATE」</h3>
     * <p>
     * 那是典型的 check-then-act 竞态：
     * <pre>
     *   A: SELECT status → ONGOING ✓
     *   B: SELECT status → ONGOING ✓      ← 两个请求都通过了检查
     *   A: UPDATE ... （成功）
     *   B: UPDATE ... （也成功）          ← 重复交卷了两遍
     * </pre>
     * 判断和动作之间只要有时间空隙，并发就会从缝隙里钻进来。
     * <b>把判断塞进 SQL 的 WHERE 里</b>，让判断和动作变成数据库层面的一次原子操作，
     * 这才是可靠的做法。
     *
     * <h3>关于返回值</h3>
     * 返回的是<b>受影响行数</b>。调用方必须检查它是否等于 1 ——
     * 等于 1 才说明自己抢到了。这是本方法唯一的"成功信号"。
     *
     * @return 受影响行数。1 表示抢到；0 表示已被别人改过（或记录不存在）
     */
    @Update("UPDATE exam_record " +
            "SET status = #{status}, submit_time = #{submitTime}, duration_used = #{durationUsed} " +
            "WHERE id = #{id} AND status = 'ONGOING'")
    int finishExam(@Param("id") Long id,
                   @Param("status") ExamStatus status,
                   @Param("submitTime") LocalDateTime submitTime,
                   @Param("durationUsed") Integer durationUsed);

    /**
     * 判分完成后写入分数。
     * <p>
     * 拆成独立的 UPDATE 而不是并进 {@link #finishExam}，
     * 是因为<b>分数要等判分跑完才知道</b>，而抢锁必须发生在判分之前
     * （否则两个请求会各判一遍，浪费资源，而且判分结果可能不一致）。
     * <p>
     * 这里不带状态条件 —— 能走到这一步说明已经抢到锁了，
     * 不需要再判断一次。
     */
    @Update("UPDATE exam_record SET score = #{score}, status = #{status} WHERE id = #{id}")
    int updateScore(@Param("id") Long id,
                    @Param("score") Integer score,
                    @Param("status") ExamStatus status);

    /**
     * 找出所有「还在进行中，但已经过了截止时间」的考试记录。
     * <p>
     * 给定时任务做超时自动交卷用。
     * <p>
     * {@code LIMIT} 是必须的：万一某个 bug 导致大量记录超时未交卷，
     * 一次全捞出来会把内存打爆。分批处理虽然慢一点，但不会把系统拖垮 ——
     * <b>批量任务永远要假设"数据可能比预期多得多"</b>。
     * <p>
     * 表上有 {@code idx_status_deadline(status, deadline)} 联合索引，
     * 所以这个查询走的是索引范围扫描，不是全表扫描。
     */
    @Select("SELECT * FROM exam_record " +
            "WHERE status = 'ONGOING' AND deadline < #{now} " +
            "ORDER BY deadline ASC LIMIT #{limit}")
    List<ExamRecord> findOverdue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 找出所有「还在进行中且尚未超时」的考试记录 —— 给定时兜底落库用。
     *
     * <h3>为什么要排除已超时的</h3>
     * <p>
     * 因为超时的那些会被 {@link #findOverdue} 交给自动交卷处理，
     * 而自动交卷本身就会把 Redis 里的答案落库。
     * 两边都处理的话，同一批数据会被写两遍 ——
     * 虽然 upsert 是幂等的、写两遍不会出错，但白白浪费一次数据库往返。
     * <p>
     * <b>两个定时任务各管一段，边界不重叠</b>，这是让批处理任务好维护的关键：
     * 每个任务处理哪些数据是明确的，不会出现"这个到底该谁管"的模糊地带。
     *
     * <h3>为什么按 deadline 升序</h3>
     * <p>
     * 一是能走 {@code idx_status_deadline(status, deadline)} 索引，
     * 省掉一次排序（索引本身就是按这个顺序组织的）。
     * <p>
     * 二是<b>语义上也更合理</b>：deadline 近的考生最快要交卷了，
     * 他们的答案最需要被落库保护。万一这次批处理没跑完（比如挂了），
     * 优先保护的是"最紧急"的那批数据。
     */
    @Select("SELECT * FROM exam_record " +
            "WHERE status = 'ONGOING' AND deadline >= #{now} " +
            "ORDER BY deadline ASC LIMIT #{limit}")
    List<ExamRecord> findOngoing(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
