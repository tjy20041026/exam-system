package com.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 作答明细，一场考试一道题一条。UK(exam_record_id, question_id) + upsert，重复写只会落到同一条记录上。 */
@Data
@TableName("answer_record")
public class AnswerRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examRecordId;

    private Long questionId;

    /** null = 学生没碰过这道题，空串 = 填了又删光。判分都算错，留着以后做学情分析。 */
    private String userAnswer;

    /** 1 对 0 错，null 是还没判（简答题交卷后一直是 null）。 */
    private Integer isCorrect;

    private Integer score;

    private LocalDateTime submitTime;
}
