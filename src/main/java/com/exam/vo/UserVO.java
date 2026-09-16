package com.exam.vo;

import com.exam.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户信息出参，不直接返回 SysUser 实体。 */
@Data
@Schema(description = "用户信息")
public class UserVO {

    @Schema(description = "用户 ID", example = "1")
    private Long id;

    @Schema(description = "登录账号", example = "student001")
    private String username;

    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @Schema(description = "角色代码", example = "STUDENT")
    private UserRole role;

    @Schema(description = "角色中文名", example = "学生")
    private String roleLabel;

    @Schema(description = "状态：1 启用，0 禁用", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
