package com.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录成功后的返回内容。
 * <p>
 * 除了 token，还把用户信息一起返回 —— 这样前端登录后立刻就能渲染出
 * 「欢迎你，张三（教师）」，不必再调一次「查询我的信息」接口。
 * 这是一个纯省事的设计，代价是响应体大了一点。
 */
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
