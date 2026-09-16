package com.exam.dto;

import com.exam.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 用户分页查询条件，字段全可选，为空表示该条件不参与过滤。 */
@Data
@Schema(description = "用户查询条件")
public class UserQueryDTO {

    @Schema(description = "账号，支持模糊匹配", example = "student")
    private String username;

    @Schema(description = "真实姓名，支持模糊匹配", example = "张")
    private String realName;

    @Schema(description = "角色", allowableValues = {"STUDENT", "TEACHER", "ADMIN"})
    private UserRole role;

    @Schema(description = "状态：1 启用，0 禁用")
    private Integer status;

    @Schema(description = "页码，从 1 开始", example = "1", defaultValue = "1")
    private Long page = 1L;

    @Schema(description = "每页条数，最大 500", example = "10", defaultValue = "10")
    private Long size = 10L;
}
