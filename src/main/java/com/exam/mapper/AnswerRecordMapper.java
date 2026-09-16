package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.entity.AnswerRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 作答明细 Mapper。 */
@Mapper
public interface AnswerRecordMapper extends BaseMapper<AnswerRecord> {

    /**
     * 批量 upsert。撞上 uk_record_question 唯一索引时转去执行 UPDATE 子句，
     * 一条语句同时覆盖首次写入和重复写入，不用先查再决定插还是改。
     * is_correct / score 一起更新，教师重新阅卷后分数才写得进去。
     * 返回的是受影响行数（INSERT 算 1、UPDATE 算 2），只能判断是否大于 0。
     */
    @Insert("<script>" +
            "INSERT INTO answer_record " +
            "(exam_record_id, question_id, user_answer, is_correct, score, submit_time) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.examRecordId}, #{item.questionId}, #{item.userAnswer}, " +
            "#{item.isCorrect}, #{item.score}, #{item.submitTime})" +
            "</foreach>" +
            " ON DUPLICATE KEY UPDATE " +
            "user_answer = VALUES(user_answer), " +
            "is_correct  = VALUES(is_correct), " +
            "score       = VALUES(score), " +
            "submit_time = VALUES(submit_time)" +
            "</script>")
    int upsertBatch(@Param("list") List<AnswerRecord> list);
}
