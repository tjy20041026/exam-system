package com.exam.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.exam.enums.QuestionType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目。类上的 autoResultMap = true 不能省：MP 读结果时不看 @TableField 上的 typeHandler，
 * 少了它 options 写进去是 JSON、查出来是 null，而且不报错。
 */
@Data
@TableName(value = "question", autoResultMap = true)
public class Question implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String content;

    private QuestionType type;

    /** 库里存 JSON，JacksonTypeHandler 负责转成 List。简答题没有选项，是 null。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<QuestionOption> options;

    /** 标准答案。单选/判断是 "A"、"对"，多选是 "A,B,D"，简答题是参考答案文本。 */
    private String answer;

    /** 建议分值，组卷时会被 PaperQuestion.score 覆盖 */
    private Integer score;

    private Integer difficulty;

    private Long creatorId;

    /** 题库可以逻辑删除：历史成绩单还引用着题目 ID，物理删了旧卷子上就是空白 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
