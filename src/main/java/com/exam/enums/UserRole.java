package com.exam.enums;

import lombok.Getter;

/**
 * 用户角色。
 * <p>
 * <b>为什么用枚举而不是直接用字符串或数字？</b>
 * <ul>
 *   <li>写 {@code "STUDEN"} 拼错时，用字符串编译器不会报错，运行时才炸；
 *       用枚举写 {@code UserRole.STUDEN} 编译直接不过</li>
 *   <li>数字（1/2/3）可读性差，看到 {@code role == 2} 得翻文档才知道是教师</li>
 *   <li>用枚举，权限判断可以写 {@code role.isTeacher()} 这种自解释的代码</li>
 * </ul>
 * <p>
 * 数据库里存的是枚举的名字（如 {@code STUDENT}），
 * 靠 {@code application.yml} 里的
 * {@code mybatis-plus.configuration.default-enum-type-handler} 指定按名字映射。
 */
@Getter
public enum UserRole {

    STUDENT("学生"),
    TEACHER("教师"),
    ADMIN("管理员");

    /** 中文显示名，用于日志和前端展示 */
    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    public boolean isStudent() {
        return this == STUDENT;
    }

    public boolean isTeacher() {
        return this == TEACHER;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }

    /** 教师或管理员都具备出题、组卷的权限 */
    public boolean canManageExam() {
        return this == TEACHER || this == ADMIN;
    }
}
