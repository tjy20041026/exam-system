package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.entity.Question;
import org.apache.ibatis.annotations.Mapper;

/** 题目 Mapper。单表增删改查 BaseMapper 都有了，暂时没有多表/聚合查询，所以是空的。 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
