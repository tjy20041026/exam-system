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
 * 考试记录，一个学生考一张卷 = 一条。id 同时也是 Redis 作答缓存的 key（exam:session:{id}），
 * deadline 开考时写死，剩余时间一律服务端算，前端倒计时只用来显示。
 */
@Data
@TableName("exam_record")
public class ExamRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private Long userId;

    private LocalDateTime startTime;

    /** 开考时间 + 试卷时长，每个考生各算各的 */
    private LocalDateTime deadline;

    private LocalDateTime submitTime;

    /** 未判卷为 null，和 0 分不是一回事 */
    private Integer score;

    private Integer durationUsed;

    private ExamStatus status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public boolean isOverdue() {
        return deadline != null && LocalDateTime.now().isAfter(deadline);
    }

    /** 进行中且没超时才能继续答题 */
    public boolean canAnswer() {
        return status == ExamStatus.ONGOING && !isOverdue();
    }

    /** 剩余秒数，超时后返回 0 而不是负数，否则前端倒计时会显示 -00:03 */
    public long remainingSeconds() {
        if (deadline == null) {
            return 0;
        }
        long seconds = java.time.Duration.between(LocalDateTime.now(), deadline).getSeconds();
        return Math.max(0, seconds);
    }
}
