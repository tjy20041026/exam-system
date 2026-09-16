package com.exam.service;

import com.exam.common.PageResult;
import com.exam.dto.UserCreateDTO;
import com.exam.dto.UserQueryDTO;
import com.exam.dto.UserUpdateDTO;
import com.exam.vo.UserVO;

/** 用户业务接口。目前只有一种实现，按 Spring 惯例保留接口层。 */
public interface SysUserService {

    /** 创建用户。账号重复时抛业务异常 */
    UserVO create(UserCreateDTO dto);

    /** 更新用户。只更新 DTO 中非 null 的字段 */
    UserVO update(Long id, UserUpdateDTO dto);

    /** 逻辑删除用户 */
    void delete(Long id);

    /** 按 ID 查询，不存在时抛业务异常 */
    UserVO getById(Long id);

    PageResult<UserVO> page(UserQueryDTO query);
}
