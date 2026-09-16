package com.exam.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.exam.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户表实体，对应表 {@code sys_user}。
 * <p>
 * 字段名用驼峰，靠 {@code map-underscore-to-camel-case: true} 自动映射到下划线列名。
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    /** 主键。IdType.AUTO 表示用数据库自增，而不是 MyBatis-Plus 默认的雪花 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号，数据库上有唯一索引 */
    private String username;

    /**
     * BCrypt 加密后的密码，绝不存明文。
     * <p>
     * <b>标 @JsonIgnore 是一道安全防线</b>：万一哪天有人图省事直接把 SysUser
     * 返回给前端（而不是转成 UserVO），这个注解能保证密码不会被序列化出去。
     * 这类"手滑"在项目赶工期时非常常见，用注解兜住比靠人记住可靠。
     */
    @JsonIgnore
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 角色。数据库存枚举名（STUDENT / TEACHER / ADMIN） */
    private UserRole role;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    // 【注意：这里没有 deleted 字段，本表刻意不做逻辑删除】
    //
    // 起因是一个真实踩到的 bug：username 上有唯一索引 uk_username，
    // 而逻辑删除只把行标记成 deleted=1，行本身还在表里，于是它仍然占着那个账号名。
    //
    // 更麻烦的是它很隐蔽：应用的查重语句会被框架自动加上 "AND deleted = 0"，
    // 所以查不到那条已删的行，认为账号可用并放行；
    // 直到 INSERT 打到数据库才报 Duplicate entry，用户拿到一个 500。
    //
    // 根本原因是【逻辑删除的作用域是应用层，唯一索引的作用域是整个表】，
    // 两者不在一个层面上，天然对不上。这不是换个配置能绕过去的。
    //
    // 本表改用物理删除，并用已有的 status 承担"停用账号"的职责 ——
    // 这才是"让某人不能登录"最贴切的表达方式：保留行、保留历史关联、还能恢复。
    //
    // 三种解法的详细对比见 sql/01_schema.sql 中 sys_user 建表语句上方的注释。

    /** 创建时间，插入时由 MetaObjectHandlerImpl 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入和更新时都自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
