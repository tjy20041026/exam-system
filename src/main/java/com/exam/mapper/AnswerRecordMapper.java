package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.entity.AnswerRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 作答明细 Mapper。
 */
@Mapper
public interface AnswerRecordMapper extends BaseMapper<AnswerRecord> {

    /**
     * 批量幂等写入作答记录。
     *
     * <h3>关键在 ON DUPLICATE KEY UPDATE 这一句</h3>
     * <p>
     * 表上有唯一索引 {@code uk_record_question(exam_record_id, question_id)}。
     * 当插入的数据撞上这个唯一索引时，MySQL 不会报错，
     * 而是转去执行 {@code UPDATE} 子句 —— 于是这一条语句
     * <b>既处理了"第一次写入"，也处理了"重复写入"</b>。
     * <p>
     * 对比一下"先查再决定插还是改"的写法：
     * <pre>
     *   SELECT ... WHERE exam_record_id=? AND question_id=?   ← 查一次
     *   if (存在) UPDATE ... else INSERT ...                  ← 再写一次
     * </pre>
     * 两次往返，而且并发下两个请求可能都查到"不存在"，然后双双 INSERT 撞车。
     * 用 upsert 的话，只有一次往返，且并发安全 ——
     * 落库这件事由数据库自己保证，不依赖应用层的判断。
     *
     * <h3>为什么更新的值要写成 VALUES(列名)</h3>
     * <p>
     * {@code VALUES(user_answer)} 在 {@code ON DUPLICATE KEY UPDATE} 子句里
     * 指的是「本次本来要插入的那个值」。这样写的好处是<b>不用把参数再传一遍</b>，
     * 也就不会出现"插入用参数 A、更新用参数 B"这种手滑。
     * <p>
     * 注意：MySQL 8.0.20 起这个写法被标记为废弃，官方推荐用别名语法
     * （{@code INSERT ... AS new ON DUPLICATE KEY UPDATE user_answer = new.user_answer}）。
     * 这里仍用老写法是为了兼容更广的 MySQL 版本，它在 8.x 上仍然正常工作。
     *
     * <h3>is_correct / score 也要更新</h3>
     * <p>
     * 因为这两个字段在"教师重新阅卷"的场景下会变。
     * 如果这里不更新它们，重新阅卷后分数就写不进去 ——
     * 这种遗漏在第一次测试时完全发现不了（因为第一次总是 INSERT 分支）。
     *
     * @param list 待写入的作答记录，调用方需保证非空
     * @return 受影响行数。注意：INSERT 算 1 行，UPDATE 算 2 行，
     *         所以这个数字<b>不能</b>用来判断"写成功了几道题"，
     *         只能用来判断是否大于 0
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
