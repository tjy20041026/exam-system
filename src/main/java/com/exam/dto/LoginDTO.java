package com.exam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 登录请求。密码只校验非空、不校验长度，否则密码策略收紧之后，老用户连登录都进不去。 */
@Data
@Schema(description = "登录请求")
public class LoginDTO {

    @Schema(description = "登录账号", example = "teacher01", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "账号不能为空")
    @Size(max = 50, message = "账号长度不能超过 50 位")
    private String username;

    @Schema(description = "登录密码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空")
    @Size(max = 32, message = "密码长度不能超过 32 位")
    private String password;
}
