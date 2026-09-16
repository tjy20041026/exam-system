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
 * 对当前这个项目来说，必要性不大：只有一种实现，接口是纯粹的多余一层。
 * 之所以还是这么写，是因为这是 Spring 项目的主流约定，绝大多数同类代码
 * 都长这样，保持结构一致本身就降低了阅读成本。
 * <p>
 * 接口真正发挥价值是在这些场景：需要多种实现（如本地缓存 vs Redis 缓存）、
 * 需要给 Service 做动态代理增强、或需要面向接口做单元测试替身。
 * 这里选择照约定写，是因为多一层接口的成本，
 * 低于"结构跟别人不一样"带来的沟通成本。
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
