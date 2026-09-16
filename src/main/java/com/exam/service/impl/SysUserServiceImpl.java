package com.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.PageResult;
import com.exam.common.ResultCode;
import com.exam.common.exception.BizException;
import com.exam.converter.UserConverter;
import com.exam.dto.UserCreateDTO;
import com.exam.dto.UserQueryDTO;
import com.exam.dto.UserUpdateDTO;
import com.exam.entity.SysUser;
import com.exam.mapper.SysUserMapper;
import com.exam.service.SysUserService;
import com.exam.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户业务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO create(UserCreateDTO dto) {
        // 1. 账号唯一性校验 —— 这是"预检"，用来给出友好提示。
        //
        //    但它【不可靠】：两个并发请求完全可能同时通过这一行检查，
        //    然后双双去 INSERT。所以它只是优化体验，不是安全保证。
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (exists > 0) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        // 存 BCrypt 哈希，绝不存明文
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setRole(dto.getRole());
        user.setStatus(1);
        // createTime / updateTime 由 MetaObjectHandlerImpl 自动填充，不必手动 set

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 2. 真正的保证在这里 —— 数据库的 uk_username 唯一索引。
            //
            //    上面的预检漏掉的那两个并发请求里，只有一个能成功 INSERT，
            //    另一个会撞上唯一索引抛 DuplicateKeyException。
            //    把它翻译成和预检失败【完全一样】的业务异常，
            //    这样两条路径对调用方来说表现一致，前端不用写两套处理逻辑。
            //
            //    这是并发编程里一个通用的思路：
            //    【乐观的预检 + 权威的兜底】。预检负责把 99% 的情况挡在数据库之外，
            //    兜底负责处理那 1% 的竞态。两者缺一不可 ——
            //    只有预检会有并发漏洞，只有兜底则会让正常用户收到难看的 500。
            log.warn("并发创建同名账号被数据库拦截: username={}", dto.getUsername());
            throw new BizException(ResultCode.USERNAME_EXISTS, "该账号已被注册，请更换");
        }

        log.info("创建用户成功: id={}, username={}, role={}",
                user.getId(), user.getUsername(), user.getRole());
        return UserConverter.toVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO update(Long id, UserUpdateDTO dto) {
        SysUser existing = userMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }

        SysUser update = new SysUser();
        update.setId(id);
        // 逐字段判断，只更新客户端确实传了的字段。
        // 注意：这里传 null 的字段 MyBatis-Plus 默认会跳过（不生成 SET 子句），
        // 所以"没传"和"传了 null"在效果上是一样的，都表示不修改。
        // 如果将来需要"把某字段清空"的语义，就得用 UpdateWrapper 显式 set null。
        if (StringUtils.hasText(dto.getRealName())) {
            update.setRealName(dto.getRealName());
        }
        if (dto.getRole() != null) {
            update.setRole(dto.getRole());
        }
        if (dto.getStatus() != null) {
            update.setStatus(dto.getStatus());
        }

        userMapper.updateById(update);

        log.info("更新用户成功: id={}", id);
        // 重新查一次再返回，而不是把 update 对象转 VO：
        // update 对象只含我们主动 set 的字段，其余全是 null。
        // 直接返回它的话，前端会看到 updateTime、createTime、username 都变成 null，
        // 然后拿这些 null 去覆盖本地状态，界面就花了。
        // 多一次查询换一个完整且真实的对象，很划算
        return UserConverter.toVO(userMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (userMapper.selectById(id) == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        // 物理删除（本表刻意不用逻辑删除，原因见 SysUser 类里的注释）。
        // 真要"停用账号"应该用 update(id, status=0)，那保留数据、还能恢复
        userMapper.deleteById(id);
        log.info("删除用户: id={}", id);
    }

    @Override
    public UserVO getById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        return UserConverter.toVO(user);
    }

    @Override
    public PageResult<UserVO> page(UserQueryDTO query) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        // 用 lambda 方法引用而不是字符串 "username"，
        // 好处是字段改名时编译器会报错，字符串写法则要等到运行时才炸
        wrapper.like(StringUtils.hasText(query.getUsername()),
                        SysUser::getUsername, query.getUsername())
                .like(StringUtils.hasText(query.getRealName()),
                        SysUser::getRealName, query.getRealName())
                .eq(query.getRole() != null,
                        SysUser::getRole, query.getRole())
                .eq(query.getStatus() != null,
                        SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getCreateTime);

        // 分页插件会自动在此处改写 SQL，追加 LIMIT 并额外执行一次 COUNT 查询
        Page<SysUser> page = new Page<>(query.getPage(), query.getSize());
        IPage<SysUser> result = userMapper.selectPage(page, wrapper);

        // 转换逻辑抽在 UserConverter 里，因为认证模块也要用同一份逻辑。
        // 方法引用 UserConverter::toVO 等价于 user -> UserConverter.toVO(user)
        return PageResult.of(result, UserConverter::toVO);
    }
}
