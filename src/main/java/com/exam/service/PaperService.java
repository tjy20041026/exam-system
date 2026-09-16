package com.exam.service;

import com.exam.common.PageResult;
import com.exam.dto.PaperComposeDTO;
import com.exam.dto.PaperCreateDTO;
import com.exam.dto.PaperQueryDTO;
import com.exam.vo.PaperDetailVO;
import com.exam.vo.PaperVO;

public interface PaperService {

    /** 创建试卷（此时还是草稿，没有题目） */
    PaperVO create(PaperCreateDTO dto);

    /** 修改试卷基本信息。已发布的试卷不允许修改 */
    PaperVO update(Long id, PaperCreateDTO dto);

    /** 组卷：全量替换题目列表并重算总分，幂等 */
    PaperDetailVO compose(Long id, PaperComposeDTO dto);

    /** 发布试卷。要求至少有一道题，且时间范围合法 */
    PaperDetailVO publish(Long id);

    /** 删除试卷。仅限草稿状态 */
    void delete(Long id);

    /** 查询试卷详情（含题目与答案，仅教师可用） */
    PaperDetailVO getDetail(Long id);

    PageResult<PaperVO> page(PaperQueryDTO query);
}
