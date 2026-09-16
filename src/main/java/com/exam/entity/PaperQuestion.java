package com.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 试卷与题目的关联，对应表 {@code paper_question}。
 *
 * <h3>多对多关系为什么需要中间表</h3>
 * <p>
 * 一张试卷有多道题，一道题也可以出现在多张试卷里 —— 典型的多对多关系。
 * 关系型数据库没法直接表达这种关系（一个单元格里塞不进一个集合），
 * 所以拆成中间表：每一行表示「某张试卷包含了某道题」这一条<b>关系</b>。
 * <p>
 * 关键点在于：<b>中间表不只是存两个 ID 的连接，它本身也是实体</b>。
 * 下面这两个字段就是证据 —— 它们描述的不是试卷、也不是题目，
 * 而是"这道题出现在这张卷子上"这件<b>事</b>的属性。
 *
 * <h3>为什么要冗余存 score</h3>
 * <p>
 * 理论上可以实时去读 {@code question.score}。之所以不这么做：
 * 同一道题在不同试卷里分值可以不同。期末卷里一道单选值 5 分，
 * 随堂小测里同一道题可能只值 1 分。如果分值只存在于 question 表上，
 * 这个需求就实现不了 —— 改一处会波及所有引用它的试卷。
 * <p>
 * 这是<b>有意的反范式冗余</b>，不是设计失误。
 * 判断标准是：冗余出来的字段，它的值是否<b>独立于源头而变化</b>。
 * 是，就该冗余；否，就该实时读。
 *
 * <h3>uk_paper_question 唯一索引的作用</h3>
 * <p>
 * {@code UNIQUE(paper_id, question_id)} 保证了同一张试卷里同一道题不会出现两次。
 * 没有它的话，教师误操作重复添加同一道题，学生就会看到同一道题出现两遍，
 * 而且两道题各自计分 —— 直接导致总分算错。
 * <p>
 * 有了它，重复添加会在数据库层被拦下，应用层只要把异常翻译成友好提示即可。
 * 这和 {@link SysUser} 的 {@code uk_username} 是同一个思路：
 * <b>能用数据库约束保证的事，不要指望应用层的检查</b> ——
 * 因为应用层的检查总会被并发绕过。
 */
@Data
@TableName("paper_question")
public class PaperQuestion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paperId;

    private Long questionId;

    /**
     * 本题在【本试卷中】的分值。
     * <p>
     * 注意不是 {@code question.score} —— 见类注释。
     */
    private Integer score;

    /** 题目顺序。学生答题时按这个顺序展示 */
    private Integer sortOrder;
}
