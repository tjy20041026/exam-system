package com.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.ResultCode;
import com.exam.common.UserContext;
import com.exam.common.exception.BizException;
import com.exam.converter.UserConverter;
import com.exam.dto.LoginDTO;
import com.exam.entity.SysUser;
import com.exam.mapper.SysUserMapper;
import com.exam.service.AuthService;
import com.exam.util.JwtUtil;
import com.exam.vo.LoginVO;
import com.exam.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));

        // 账号不存在和密码错误返回同一个错误码。区分开的话，登录接口
        // 就成了账号枚举工具，拿一份字典刷一遍就能筛出哪些账号真实存在

        if (user == null) {
            log.warn("登录失败（账号不存在）: username={}", dto.getUsername());
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }

        // matches(明文, 密文)，顺序反了会一直返回 false
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            log.warn("登录失败（密码错误）: username={}", dto.getUsername());
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }

        // 先验密码再看启用状态。反过来的话，登录接口就成了
        // "这个账号是否被禁用"的查询接口
        if (!isEnabled(user)) {
            log.warn("登录失败（账号已禁用）: username={}", dto.getUsername());
            throw new BizException(ResultCode.USER_DISABLED);
        }

        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());

        log.info("登录成功: userId={}, username={}, role={}",
                user.getId(), user.getUsername(), user.getRole());

        return new LoginVO(token, jwtUtil.getExpireSeconds(), UserConverter.toVO(user));
    }

    @Override
    public UserVO currentUser() {
        // 未登录时 UserContext 直接抛 401，走到这里说明拦截器已放行
        Long userId = UserContext.getUserId();

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            // token 合法却查不到人，只可能是账号在 token 有效期内被删了。
            // 返回 401 让客户端清掉本地 token 重登，比 404 更合理
            throw new BizException(ResultCode.UNAUTHORIZED, "账号不存在或已被删除");
        }
        return UserConverter.toVO(user);
    }

    /** status 为 1 表示启用；null 当禁用处理 */
    private boolean isEnabled(SysUser user) {
        return user.getStatus() != null && user.getStatus() == 1;
    }
}
