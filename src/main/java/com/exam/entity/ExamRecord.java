package com.exam.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.exam.enums.ExamStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 考试记录。一个学生考一张试卷 = 一条记录。
 *
 * <h3>这是整个项目里最核心的一张表</h3>
 * <p>
 * 因为它同时承担了三个角色：
 * <ol>
 *   <li><b>身份</b>：谁在考哪张卷（user_id + paper_id）</li>
 *   <li><b>时间基准</b>：deadline 决定了什么时候必须交卷</li>
 *   <li><b>状态机</b>：status 决定了现在能做哪些操作</li>
 * </ol>
 * <p>
 * 它的主键 {@code id} 还有第四个用途：作为 Redis 里作答缓存的 key 的一部分
 * （{@code exam:session:{examRecordId}}）。Day 6 会用到。
 *
 * <h3>deadline 为什么要在开考时就写死</h3>
 * <p>
 * 因为剩余时间必须由【服务端】说了算。
 * <p>
 * 如果让前端自己倒计时，改一下系统时间或者直接改 JS 变量就能"续命"；
 * 而且网络抖动、页面刷新都会让计时不准。把截止时刻固定在服务端，
 * 前端每次刷新都能从"剩余 = deadline - 现在"重新算出来，
 * 无论刷新多少次、换多少台设备，剩余时间都是准确的。
 * <p>
 * 这也是为什么下面没有"已用时长"这种需要客户端上报的字段 ——
 * 凡是客户端能伪造的数据，都不该作为判定依据。
 */
@Data
@TableName("exam_record")
public class ExamRecord implements Serializable {

    /** 主键。同时是 Redis session key 的组成部分 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private Long userId;

    /** 实际开考时间 */
    private LocalDateTime startTime;

    /**
     * 交卷截止时间 = 开考时间 + 试卷时长。
     * <p>
     * 注意是<b>每个考生各自计算</b>的，不是试卷上的固定值 ——
     * 同一张试卷，9:00 开考的人 10:00 截止，9:30 开考的人 10:30 截止。
     */
    private LocalDateTime deadline;

    /** 实际交卷时间，未交卷为 null */
    private LocalDateTime submitTime;

    /** 得分。未判卷为 null。注意 null 和 0 的含义完全不同 */
    private Integer score;

    /** 实际用时（秒） */
    private Integer durationUsed;

    private ExamStatus status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ==================== 领域行为 ====================

    /**
     * 判断是否已超过截止时间。
     * <p>
     * 把这个判断写在实体上而不是散落在 Service 里，是因为
     * "当前时刻相对 deadline 的位置"是这个实体自己的属性。
     * 换任何地方用，逻辑都该是同一套。
     */
    public boolean isOverdue() {
        return deadline != null && LocalDateTime.now().isAfter(deadline);
    }

    /** 还能作答吗 —— 既要在进行中，又不能超时 */
    public boolean canAnswer() {
        return status == ExamStatus.ONGOING && !isOverdue();
    }

    /**
     * 剩余秒数。
     * <p>
     * 用 {@code Math.max(0, ...)} 兜底：超时后这个值应该是 0，
     * 而不是负数。返回负数会让前端的倒计时显示成 "-00:03" 这种怪东西。
     */
    public long remainingSeconds() {
        if (deadline == null) {
            return 0;
        }
        long seconds = java.time.Duration.between(LocalDateTime.now(), deadline).getSeconds();
        return Math.max(0, seconds);
    }
}
