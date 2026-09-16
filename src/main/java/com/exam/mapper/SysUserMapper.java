package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper。
 * <p>
 * 继承 {@code BaseMapper<SysUser>} 即可白拿一整套单表 CRUD：
 * insert / deleteById / updateById / selectById / selectList / selectPage ...
 * 一行 SQL 都不用写。
 * <p>
 * 只有当查询逻辑复杂到单表搞不定时（多表 join、动态条件拼装），
 * 才在这里追加自定义方法或写 XML。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
