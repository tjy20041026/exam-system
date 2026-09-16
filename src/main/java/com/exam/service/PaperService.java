package com.exam.service;

import com.exam.common.PageResult;
import com.exam.dto.PaperComposeDTO;
import com.exam.dto.PaperCreateDTO;
import com.exam.dto.PaperQueryDTO;
import com.exam.vo.PaperDetailVO;
import com.exam.vo.PaperVO;

/**
 * 试卷服务。
 */
public interface PaperService {

    /** 创建试卷（此时还是草稿，没有题目） */
    PaperVO create(PaperCreateDTO dto);

    /** 修改试卷基本信息。已发布的试卷不允许修改 */
    PaperVO update(Long id, PaperCreateDTO dto);

    /**
     * 组卷：全量替换试卷的题目列表，并重算总分。
     * <p>
     * 这个接口是幂等的 —— 同样的请求调多次，结果一致。
     */
    PaperDetailVO compose(Long id, PaperComposeDTO dto);

    /** 发布试卷。要求至少有一道题，且时间范围合法 */
    PaperDetailVO publish(Long id);

    // 【待补充】withdraw(Long id) —— 撤回为草稿
    //
    // 撤回的安全前提是「还没有任何人开考过」。这个检查需要查 exam_record 表，
    // 而那张表的实体还没写（Day 5 才有考试流程）。
    //
    // 现在先把方法留在外面，而不是写一个「只检查状态、不检查考试记录」的版本 ——
    // 那种实现看起来能用，实际上会在有人已经开考的情况下允许撤回，
    // 让那些学生的考试失去依据。宁可这个功能暂时不存在，
    // 也不要一个【看起来是对的】的错误实现。

    /** 删除试卷。仅限草稿状态 */
    void delete(Long id);

    /** 查询试卷详情（含题目与答案，仅教师可用） */
    PaperDetailVO getDetail(Long id);

    /** 分页查询试卷列表 */
    PageResult<PaperVO> page(PaperQueryDTO query);
}
