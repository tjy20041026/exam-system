package com.exam.service;

import com.exam.dto.SaveAnswerDTO;
import com.exam.vo.ExamPaperVO;
import com.exam.vo.ExamRecordVO;
import com.exam.vo.ExamResultVO;

import java.util.List;

/**
 * 考试主流程服务。
 * <p>
 * 除了开考收 paperId，后面都收 examRecordId：开考之后主体是"这场考试"，
 * 是谁的、做到哪一步、什么时候截止都能从这条记录推出来。
 */
public interface ExamService {

    /** 开考，也是恢复考试的入口。幂等：已有进行中的记录就直接返回它，不报错（点两下、刷新重进都会重复触发）。 */
    ExamPaperVO startExam(Long paperId);

    /** 取卷。返回值带已答内容，刷新和断点续考靠它恢复现场。 */
    ExamPaperVO getPaper(Long examRecordId);

    /** 单题答案入库。INSERT ... ON DUPLICATE KEY UPDATE，反复改同一题不会留多条记录。 */
    void saveAnswer(Long examRecordId, SaveAnswerDTO dto);

    /** 交卷。防重复靠 UPDATE ... WHERE status='ONGOING' 条件更新，行锁保证只有一个请求 affectedRows==1；Redis 那把锁只削峰。 */
    void submit(Long examRecordId);

    /** 查成绩。没交卷报 EXAM_NOT_SUBMITTED —— "没考完"和"考了 0 分"不是一回事。 */
    ExamResultVO getResult(Long examRecordId);

    /** 我的考试列表，按开考时间倒序 */
    List<ExamRecordVO> myRecords();

    // 下面三个只给定时任务调，没有对应的 HTTP 接口

    /** 超时自动交卷。故意不校验归属：调用方是定时任务，没有当前登录用户这回事，且唯一调用点只喂进来超时的记录；要给它开 HTTP 接口时必须补校验。 */
    void autoSubmit(Long examRecordId);

    /** 查超时未交卷的记录 ID。limit 是单次上限，理由见 ExamRecordMapper#findOverdue */
    List<Long> findOverdueExamRecordIds(int limit);

    /**
     * 兜底落库：把进行中考试的 Redis 作答态刷进 MySQL。
     * Redis 是内存库，机器故障或误 FLUSHDB 都会丢，不能只存一份。间隔取数据安全和库压力之间的平衡。
     */
    int flushOngoingSessions(int limit);
}
