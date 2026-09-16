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
 * 用户表。本表刻意不做逻辑删除：username 上有唯一索引，逻辑删除的行仍占着账号名，
 * 应用层查重（被自动拼上 deleted = 0）查不到它，一直到 INSERT 才报 Duplicate entry。禁用账号用 status。
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 密文。加 @JsonIgnore 是防着哪天有人直接把实体返回给前端 */
    @JsonIgnore
    private String password;

    private String realName;

    private UserRole role;

    /** 1 启用 0 禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
