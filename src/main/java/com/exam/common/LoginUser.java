package com.exam.common;

import com.exam.enums.UserRole;

/** 当前登录用户的身份信息。record 保证不可变，刻意不含密码 —— 它会在整个请求链路里传递。 */
public record LoginUser(Long userId, String username, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    /** 教师和管理员都能管理考试 */
    public boolean canManageExam() {
        return role != null && role.canManageExam();
    }
}
