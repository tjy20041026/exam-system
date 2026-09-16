package com.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 作答明细。一场考试里一道题 = 一条记录。
 *
 * <h3>关于 uk_record_question 唯一索引配合 upsert</h3>
 * <p>
 * 表上有 {@code UNIQUE(exam_record_id, question_id)}。
 * 配合 SQL 的 {@code INSERT ... ON DUPLICATE KEY UPDATE}，
 * 一次写入既可以是"新增答案"，也可以是"修改答案"，不用先查再判断。
 * <p>
 * 这个性质叫<b>幂等</b>：同样的写入执行一次和执行十次，结果相同。
 * <p>
 * 幂等在这里是刚需，因为答案会被写很多次：
 * <ul>
 *   <li>学生在答题过程中反复修改同一道题</li>
 *   <li>交卷时把 Redis 里的答案落库</li>
 *   <li>定时任务兜底落库（可能和交卷的重叠）</li>
 *   <li>重复交卷的请求（并发下可能发生）</li>
 * </ul>
 * <p>
 * 没有幂等性的话，上面每一条都可能产生重复行，
 * 而重复的作答记录会让总分凭空多出来 —— 这种 bug 而且很难在测试中发现。
 */
@Data
@TableName("answer_record")
public class AnswerRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属的考试记录 ID */
    private Long examRecordId;

    private Long questionId;

    /**
     * 考生答案。
     * <p>
     * <b>null 表示"未作答"</b>，和空字符串 "" 有区别：
     * 空字符串是"学生点开题目、删光了内容、确认提交"，
     * 而 null 是"这道题学生根本没碰"。
     * <p>
     * 本项目的判分对两者一视同仁（都算错），但保留这个区别是为了
     * 将来能做学情分析 —— "没来得及做"和"做了但不会"是两回事，
     * 对教学的指导意义完全不同。
     */
    private String userAnswer;

    /**
     * 是否正确：1 对，0 错，<b>null 表示尚未判定</b>。
     * <p>
     * 简答题永远是 null，因为它需要人工阅卷。
     * 前端靠这个字段区分"这题答错了"和"这题还没批"。
     */
    private Integer isCorrect;

    /** 本题实得分。简答题阅卷前为 0 */
    private Integer score;

    /** 本题最后作答时间 */
    private LocalDateTime submitTime;
}
