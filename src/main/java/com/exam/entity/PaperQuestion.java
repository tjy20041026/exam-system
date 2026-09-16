package com.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/** 试卷-题目中间表，多对多。UK(paper_id, question_id) 挡住重复加同一道题。 */
@Data
@TableName("paper_question")
public class PaperQuestion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private Long questionId;

    /** 本卷内的分值，不是 question.score —— 同一道题在不同卷子里可以值不同的分。 */
    private Integer score;

    /** 学生答题时的展示顺序 */
    private Integer sortOrder;
}
