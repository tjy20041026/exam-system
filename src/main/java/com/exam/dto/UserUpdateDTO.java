package com.exam.dto;

import com.exam.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 更新用户请求。不含 username 和 password：账号是各种业务数据的关联键不能改，改密码走单独的接口。 */
@Data
@Schema(description = "更新用户请求")
public class UserUpdateDTO {

    @Schema(description = "真实姓名", example = "李四")
    @Size(max = 50, message = "姓名长度不能超过 50")
    private String realName;

    @Schema(description = "角色", example = "TEACHER",
            allowableValues = {"STUDENT", "TEACHER", "ADMIN"})
    private UserRole role;

    @Schema(description = "状态：1 启用，0 禁用", example = "1")
    private Integer status;
}
