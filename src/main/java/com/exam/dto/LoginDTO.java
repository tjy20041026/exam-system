package com.exam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求参数。
 * <p>
 * <b>注意这里对密码的校验规则和 {@code UserCreateDTO} 不一样</b>：
 * 创建用户时校验「长度 6-32 位」，那是为了拦住用户设置弱密码；
 * 登录时只校验「不能为空」，不校验长度。
 * <p>
 * 原因是：如果登录时也校验「至少 6 位」，那么当密码策略从 6 位改成 8 位之后，
 * 那些用 6 位老密码注册的用户会<b>连登录都进不去</b>，
 * 直接收到「密码长度不足」这种莫名其妙的提示 —— 他明明输的是对的。
 * 登录的逻辑应该是「拿你给的凭据去比对」，而不是「先评判这个密码合不合格」。
 */
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
