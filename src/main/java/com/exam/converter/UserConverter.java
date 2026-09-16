package com.exam.converter;

import com.exam.entity.SysUser;
import com.exam.vo.UserVO;

/**
 * {@code SysUser} → {@code UserVO} 的转换。
 *
 * <h3>为什么把它单独抽出来</h3>
 * <p>
 * 这个转换逻辑最初写在 {@code SysUserServiceImpl} 里当私有方法。
 * 但写认证模块时发现登录也要把用户转成 VO —— 总不能为了复用去调用
 * 用户模块的 Service（那会让「认证」依赖「用户管理」，方向反了），
 * 更不能复制一份（两份代码将来必然只改一份，然后线上出现
 * 「列表里的角色显示对、登录返回的角色显示错」这种见鬼的问题）。
 * <p>
 * 所以抽成一个无状态的工具类。它不依赖任何 Bean，用静态方法就够了，
 * 不必做成 Spring 组件 —— <b>不是所有东西都需要交给容器管理的</b>。
 *
 * <h3>为什么是手写而不是 BeanUtils.copyProperties</h3>
 * <ul>
 *   <li><b>性能</b>：反射逐字段拷贝比直接赋值慢一个数量级。
 *       列表接口一次转几十上百条时差距会显现出来</li>
 *   <li><b>安全</b>：反射按<b>同名</b>字段拷贝。万一将来实体加了敏感字段
 *       （比如 {@code idCard}），而 VO 也刚好有同名字段，
 *       敏感数据会被静默带出去，代码 review 时根本看不出来。
 *       手写的话，多带一个字段是显式写出来的，一眼可见</li>
 *   <li><b>可控</b>：可以顺手加工派生字段（下面的 {@code roleLabel}），
 *       反射拷贝做不到，还得额外补一段代码</li>
 * </ul>
 */
public final class UserConverter {

    private UserConverter() {
    }

    /**
     * 转换为对外 VO。
     * <p>
     * 刻意<b>不</b>拷贝 {@code password} 和 {@code deleted} 字段。
     * 虽然实体上已经标了 {@code @JsonIgnore} 兜底，但那是序列化层的最后一道防线，
     * 不该被当成唯一防线 —— 万一哪天有人把 VO 直接拿去做了别的用途
     * （写日志、发消息），序列化层就管不着了。
     *
     * @return 入参为 {@code null} 时返回 {@code null}，方便调用方链式使用
     */
    public static UserVO toVO(SysUser user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        // 派生字段：把枚举翻译成中文给前端显示。
        // 在服务端翻译而不是让前端自己维护一份映射表，
        // 是为了将来加角色时只改一处
        vo.setRoleLabel(user.getRole() == null ? null : user.getRole().getLabel());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
