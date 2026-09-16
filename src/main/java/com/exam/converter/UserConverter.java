package com.exam.converter;

import com.exam.entity.SysUser;
import com.exam.vo.UserVO;

/**
 * SysUser -> UserVO 转换。抽成静态工具类：登录和用户列表都要转 VO，放 Service 里会让认证反向依赖用户管理。
 * 手写不用 BeanUtils.copyProperties —— 反射慢，而且按同名字段拷贝，实体将来加了敏感字段会被静默带出去。
 */
public final class UserConverter {

    private UserConverter() {
    }

    /** 转成对外 VO。刻意不拷贝 password / deleted，实体上的 @JsonIgnore 只管序列化这一层。 */
    public static UserVO toVO(SysUser user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        // 枚举翻成中文，省得前端自己维护一份映射表
        vo.setRoleLabel(user.getRole() == null ? null : user.getRole().getLabel());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
