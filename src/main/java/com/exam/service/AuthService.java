package com.exam.service;

import com.exam.dto.LoginDTO;
import com.exam.vo.LoginVO;
import com.exam.vo.UserVO;

/**
 * 认证服务。
 */
public interface AuthService {

    /** 登录，成功返回 token 和用户信息 */
    LoginVO login(LoginDTO dto);

    /**
     * 查询当前登录用户。
     * <p>
     * 注意这里是<b>重新查一次数据库</b>，而不是直接返回 token 里解出来的信息。
     * <p>
     * 因为 token 一旦签发就无法更改，里面的角色、账号状态都是<b>签发那一刻</b>的快照。
     * 如果管理员在用户登录后把他的账号禁用了，只要 token 还没过期，
     * 他就能继续用 —— 这正是 JWT 无状态特性的代价。
     * 重查数据库能缓解这一点（至少这个接口是准的），
     * 彻底解决则需要在拦截器里每次都查库，或者引入 token 黑名单。
     */
    UserVO currentUser();
}
