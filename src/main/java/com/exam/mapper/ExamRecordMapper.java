package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.entity.ExamRecord;
import com.exam.enums.ExamStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 考试记录 Mapper。 */
@Mapper
public interface ExamRecordMapper extends BaseMapper<ExamRecord> {

    /**
     * 条件更新结束考试。WHERE 里的 status = 'ONGOING' 就是防重复交卷的关键：
     * 并发时只有一个请求能改到这行（返回 1），其余 WHERE 匹配不上、返回 0，当成重复提交拒掉。
     */
    @Update("UPDATE exam_record " +
            "SET status = #{status}, submit_time = #{submitTime}, duration_used = #{durationUsed} " +
            "WHERE id = #{id} AND status = 'ONGOING'")
    int finishExam(@Param("id") Long id,
                   @Param("status") ExamStatus status,
                   @Param("submitTime") LocalDateTime submitTime,
                   @Param("durationUsed") Integer durationUsed);

    /** 判分后写分数。分数要等判分跑完才知道，所以跟结束考试拆成两条；这里不再带状态条件。 */
    @Update("UPDATE exam_record SET score = #{score}, status = #{status} WHERE id = #{id}")
    int updateScore(@Param("id") Long id,
                    @Param("score") Integer score,
                    @Param("status") ExamStatus status);

    /** 超时未交卷的记录，给自动交卷用。LIMIT 防止一次捞太多。 */
    @Select("SELECT * FROM exam_record " +
            "WHERE status = 'ONGOING' AND deadline < #{now} " +
            "ORDER BY deadline ASC LIMIT #{limit}")
    List<ExamRecord> findOverdue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 未超时的进行中记录，给兜底落库用。已超时的归 findOverdue 管，两边边界不重叠。 */
    @Select("SELECT * FROM exam_record " +
            "WHERE status = 'ONGOING' AND deadline >= #{now} " +
            "ORDER BY deadline ASC LIMIT #{limit}")
    List<ExamRecord> findOngoing(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
