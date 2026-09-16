package com.exam.common;

import com.exam.enums.UserRole;

/**
 * 当前登录用户的身份信息。
 * <p>
 * 用 {@code record} 而不是普通类：Java 16 引入的 record 是<b>不可变</b>的，
 * 字段一旦构造就不能再改。登录身份这种数据本来就该只读，
 * 用 record 可以让"不可篡改"这件事由编译器来保证，而不是靠自觉。
 * <p>
 * 同时 record 自动生成了构造器、getter、{@code equals}、{@code hashCode}、{@code toString}，
 * 省掉一大堆 Lombok 注解。
 * <p>
 * <b>注意这里刻意不含密码</b> —— 身份信息会在整个请求链路里被到处传递，
 * 往里面塞密码就等于把密码撒得到处都是。
 *
 * @param userId   用户 ID
 * @param username 登录账号
 * @param role     角色
 */
public record LoginUser(Long userId, String username, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    /** 教师和管理员都能管理考试 */
    public boolean canManageExam() {
        return role != null && role.canManageExam();
    }
}
