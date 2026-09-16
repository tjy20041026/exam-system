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
        // 唯一性预检，只为给出友好提示。两个并发请求可能同时通过这一行，
        // 真正的保证是下面 catch 里的唯一索引
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (exists > 0) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setRole(dto.getRole());
        user.setStatus(1);
        // createTime / updateTime 由 MetaObjectHandlerImpl 自动填充，不必手动 set

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 预检漏掉的那两个并发请求里只有一个能 INSERT 成功，
            // 另一个撞上 uk_username 走到这里。翻译成和预检失败一样的业务异常，
            // 两条路径对调用方表现一致
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
        // MyBatis-Plus 默认跳过 null 字段（不生成 SET 子句），所以"没传"和
        // "传 null"效果一样，都表示不改。将来若要支持"清空某字段"，
        // 得换 UpdateWrapper 显式 set null
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
        // 重新查一次再返回：update 对象只有主动 set 过的字段，其余全是 null，
        // 转成 VO 给前端会拿这些 null 覆盖本地状态
        return UserConverter.toVO(userMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (userMapper.selectById(id) == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        // 物理删除（本表不用逻辑删除，原因见 SysUser 类注释）。
        // 停用账号应该走 update(id, status=0)，保留数据还能恢复
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
        // 用 lambda 方法引用而不是字符串字段名，改名时编译期就会报错
        wrapper.like(StringUtils.hasText(query.getUsername()),
                        SysUser::getUsername, query.getUsername())
                .like(StringUtils.hasText(query.getRealName()),
                        SysUser::getRealName, query.getRealName())
                .eq(query.getRole() != null,
                        SysUser::getRole, query.getRole())
                .eq(query.getStatus() != null,
                        SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getCreateTime);

        Page<SysUser> page = new Page<>(query.getPage(), query.getSize());
        IPage<SysUser> result = userMapper.selectPage(page, wrapper);

        return PageResult.of(result, UserConverter::toVO);
    }
}
