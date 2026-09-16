package com.exam.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.exam.enums.PaperStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 试卷。题目和分值在 paper_question 里，这里只放卷子本身的属性。 */
@Data
@TableName("paper")
public class Paper implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** 组卷时由各题分值累加得出，不接受客户端传值：算得出来的字段只能有一个写入方。 */
    private Integer totalScore;

    /** 考试时长（分钟） */
    private Integer duration;

    /** null 表示不限制开考时间 */
    private LocalDateTime startTime;

    /** null 表示不限制截止时间 */
    private LocalDateTime endTime;

    private PaperStatus status;

    private Long creatorId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
