package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.entity.Question;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目 Mapper。
 * <p>
 * 继承 {@code BaseMapper<Question>} 就白得了单表增删改查、
 * 条件构造器查询、分页等一整套方法，一行 SQL 都不用写。
 * <p>
 * 复杂查询（多表 JOIN、聚合统计）才需要在这里加自定义方法，
 * 配套的 SQL 写在 {@code resources/mapper/XxxMapper.xml} 里 ——
 * 但目前还没有这种需求，所以这个接口是空的。
 * <p>
 * <b>不要为了"看起来完整"而提前写一堆用不上的方法。</b>
 * 空接口在这里恰恰说明设计是对的：单表操作全被 BaseMapper 覆盖了。
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
