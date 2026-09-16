package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.dto.PaperQuestionCount;
import com.exam.entity.PaperQuestion;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 试卷题目关联 Mapper。 */
@Mapper
public interface PaperQuestionMapper extends BaseMapper<PaperQuestion> {

    /**
     * 批量插入。拼成一条 INSERT 发过去，不循环调 insert()——组卷一次几十道题，循环就是几十次往返。
     * 值一律用 #{}（占位符）别用 ${}（文本替换会注入）；list 为空会拼出语法错误的 SQL。
     */
    @Insert("<script>" +
            "INSERT INTO paper_question (paper_id, question_id, score, sort_order) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.paperId}, #{item.questionId}, #{item.score}, #{item.sortOrder})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<PaperQuestion> list);

    /** 批量统计多张试卷的题目数。按试卷逐个 count 就是 N+1。 */
    @Select("<script>" +
            "SELECT paper_id AS paperId, COUNT(*) AS questionCount " +
            "FROM paper_question " +
            "WHERE paper_id IN " +
            "<foreach collection='paperIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " GROUP BY paper_id" +
            "</script>")
    List<PaperQuestionCount> countByPaperIds(@Param("paperIds") List<Long> paperIds);
}
