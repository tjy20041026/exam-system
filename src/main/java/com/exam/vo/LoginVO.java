package com.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 登录成功后返回 token 和用户信息，省得前端再查一次。 */
@Data
@Schema(description = "登录结果")
public class LoginVO {

    @Schema(description = "访问令牌，后续请求需放在 Authorization 头里，格式：Bearer <token>")
    private String token;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "有效期（秒）", example = "7200")
    private Long expiresIn;

    @Schema(description = "登录用户信息")
    private UserVO user;

    public LoginVO(String token, Long expiresIn, UserVO user) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.user = user;
    }
}
