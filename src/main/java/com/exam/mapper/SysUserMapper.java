package com.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/** 用户表 Mapper。单表操作 BaseMapper 够用，没有自定义 SQL。 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
