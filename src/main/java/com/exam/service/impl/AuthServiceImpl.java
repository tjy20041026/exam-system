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

        // ---------- 反模式警告：这里刻意"含糊其辞" ----------

        // 写法一（❌ 不要这样）：
        //   if (user == null) throw new BizException("用户不存在");
        //   if (!encoder.matches(...)) throw new BizException("密码错误");
        // 看起来提示更友好，实际上提供了【账号枚举】通道：
        // 攻击者拿一份手机号/工号字典逐个试，根据返回的提示不同，
        // 就能筛出哪些账号真实存在，再去针对性地爆破密码。
        // 对考试系统来说，等于把全校学生的账号名单免费送出去。

        // 写法二（✅ 本项目的做法）：
        //   账号不存在和密码错误返回【完全相同】的错误码与提示。
        // 攻击者无法区分这两种情况，枚举通道就被堵上了。

        if (user == null) {
            log.warn("登录失败（账号不存在）: username={}", dto.getUsername());
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }

        // matches(明文, 密文)：BCrypt 从密文里取出盐和成本因子，
        // 用同样的参数把明文算一遍再比对。
        // 注意顺序是（明文, 密文），反了会一直返回 false —— 这是很常见的写错点
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            log.warn("登录失败（密码错误）: username={}", dto.getUsername());
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }

        // ---------- 到这里身份已确认，再检查账号是否可用 ----------
        // 顺序很重要：先验密码再看启用状态。
        // 反过来的话，登录接口就变成了一个"这个账号是否被禁用"的查询接口，
        // 同样属于信息泄露
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
        // UserContext.getUserId() 在未登录时会抛 401，
        // 所以走到这里说明拦截器已经放行了，userId 一定有效
        Long userId = UserContext.getUserId();

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            // 能通过拦截器说明 token 是合法的，但用户却查不到 ——
            // 只可能是账号在 token 有效期内被删掉了。
            // 返回 401 让客户端清掉本地 token 重新登录，比返回 404 更合理
            throw new BizException(ResultCode.UNAUTHORIZED, "账号不存在或已被删除");
        }
        return UserConverter.toVO(user);
    }

    /** status 为 1 表示启用。null 视为禁用，宁可拒绝也不放行 */
    private boolean isEnabled(SysUser user) {
        return user.getStatus() != null && user.getStatus() == 1;
    }
}
