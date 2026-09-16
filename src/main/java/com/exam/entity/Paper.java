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

/**
 * 试卷实体，对应表 {@code paper}。
 */
@Data
@TableName("paper")
public class Paper implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 试卷标题 */
    private String title;

    /**
     * 总分。
     * <p>
     * <b>这个值不允许客户端直接设置</b>，而是由组卷时各题分值累加得出。
     * 原因是它属于<b>派生数据</b> —— 真正的源头是 paper_question 里每一题的分值。
     * 如果能手工设置，就必然会出现"各题相加是 95，总分写的是 100"的矛盾，
     * 而且是静默的：界面上显示 100 分，学生实际最多只能拿到 95。
     * <p>
     * 这类"可以由其他字段算出来的字段"，只应该有一个地方能改它。
     * 数据库设计里管这叫<b>反范式</b>：存它是为了查询方便（列表页不用每次都 SUM），
     * 代价就是必须保证它永远和源头一致。
     */
    private Integer totalScore;

    /** 考试时长（分钟） */
    private Integer duration;

    /** 开考时间。null 表示不限制 */
    private LocalDateTime startTime;

    /** 截止时间。null 表示不限制 */
    private LocalDateTime endTime;

    /** 状态：DRAFT / PUBLISHED / FINISHED */
    private PaperStatus status;

    /** 创建教师 ID */
    private Long creatorId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
