package com.exam.service;

import com.exam.common.PageResult;
import com.exam.dto.UserCreateDTO;
import com.exam.dto.UserQueryDTO;
import com.exam.dto.UserUpdateDTO;
import com.exam.vo.UserVO;

/**
 * 用户业务接口。
 * <p>
 * <b>为什么要有接口，直接写实现类不行吗？</b>
 * <p>
 * 对当前这个项目来说，说实话：必要性不大。只有一种实现，接口是纯粹的多余一层。
 * 之所以还是这么写，是因为这是 Spring 项目的主流约定，你在别处看到的代码
 * 绝大多数都长这样，保持一致便于对照学习。
 * <p>
 * 接口真正发挥价值是在这些场景：需要多种实现（如本地缓存 vs Redis 缓存）、
 * 需要给 Service 做动态代理增强、或需要面向接口做单元测试替身。
 * <b>面试时如果被问到，能说清"这里其实可以不要接口"比硬答"解耦"更加分</b> ——
 * 说明你是按需选择而非照搬教条。
 */
public interface SysUserService {

    /** 创建用户。账号重复时抛业务异常 */
    UserVO create(UserCreateDTO dto);

    /** 更新用户。只更新 DTO 中非 null 的字段 */
    UserVO update(Long id, UserUpdateDTO dto);

    /** 逻辑删除用户 */
    void delete(Long id);

    /** 按 ID 查询，不存在时抛业务异常 */
    UserVO getById(Long id);

    /** 分页查询 */
    PageResult<UserVO> page(UserQueryDTO query);
}
