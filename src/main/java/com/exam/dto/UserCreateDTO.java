package com.exam.dto;

import com.exam.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建用户请求。不直接用实体接参：实体里的 id、createTime、deleted 不该由客户端决定。 */
@Data
@Schema(description = "创建用户请求")
public class UserCreateDTO {

    @Schema(description = "登录账号，4-50 位字母数字下划线", example = "student001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 50, message = "账号长度需在 4-50 位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "账号只能包含字母、数字和下划线")
    private String username;

    @Schema(description = "密码，6-32 位", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
    private String password;

    @Schema(description = "真实姓名", example = "张三", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50")
    private String realName;

    @Schema(description = "角色", example = "STUDENT", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"STUDENT", "TEACHER", "ADMIN"})
    @NotNull(message = "角色不能为空")
    private UserRole role;
}
