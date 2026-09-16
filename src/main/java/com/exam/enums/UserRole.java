package com.exam.enums;

import lombok.Getter;

/** 用户角色。库里存枚举名，靠 default-enum-type-handler 按名字映射。 */
@Getter
public enum UserRole {

    STUDENT("学生"),

    TEACHER("教师"),

    ADMIN("管理员");

    /** 中文显示名，给日志和前端用 */
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

    /** 教师或管理员才能出题、组卷 */
    public boolean canManageExam() {
        return this == TEACHER || this == ADMIN;
    }
}
