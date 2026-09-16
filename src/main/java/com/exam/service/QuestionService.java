package com.exam.service;

import com.exam.common.PageResult;
import com.exam.dto.QuestionCreateDTO;
import com.exam.dto.QuestionQueryDTO;
import com.exam.dto.QuestionUpdateDTO;
import com.exam.vo.QuestionVO;

/**
 * 题库服务。
 */
public interface QuestionService {

    /** 录入题目。出题人取当前登录用户 */
    QuestionVO create(QuestionCreateDTO dto);

    /** 修改题目 */
    QuestionVO update(Long id, QuestionUpdateDTO dto);

    /** 删除题目。已被试卷引用的题目不允许删除 */
    void delete(Long id);

    /** 查询题目详情（含答案） */
    QuestionVO getById(Long id);

    /** 分页查询题库 */
    PageResult<QuestionVO> page(QuestionQueryDTO query);
}
