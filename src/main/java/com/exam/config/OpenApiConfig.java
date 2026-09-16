package com.exam.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 接口文档元信息配置。不配的话 springdoc 吐出来的标题是 "OpenAPI definition"、版本 v0。 */
@Configuration
public class OpenApiConfig {

    /**
     * 文档全局元信息 + 安全方案。
     * <p>
     * 安全方案必须挂在 OpenAPI 对象上，单独声明一个 Components bean 是没人读的 ——
     * 现象是文档能打开、info 也正常，但 Authorize 按钮压根不出现，只能打开
     * /v3/api-docs 核对 JSON 才能发现。
     */
    @Bean
    public OpenAPI examOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("在线考试系统 API")
                        .version("v1.0")
                        .description("""
                                纯后端的在线考试系统，覆盖「出题 → 组卷 → 考试 → 交卷 → 判分 → 查成绩」完整闭环。

                                调 POST /api/auth/login 拿 token，点右上角 Authorize 填进去即可，
                                不用手动加 Bearer 前缀。测试账号：

                                admin / Admin@123（管理员）
                                teacher01 / Teacher@123（教师）
                                student01 / Student@123（学生）

                                考试相关接口只能用学生账号调，教师账号会被 403 拒绝。
                                学生端返回的题目不含标准答案，交卷后也不返回，避免先交卷的泄题给后考的。
                                """)
                        .contact(new Contact()
                                .name("唐靖祎")
                                .email("tangjingyi@example.com")))
                .components(examComponents());
    }

    /** JWT 安全方案。名字要和控制器上 {@code @SecurityRequirement} 里写的字符串完全一致 */
    private Components examComponents() {
        return new Components().addSecuritySchemes("Authorization",
                new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        // 必须是 "bearer"，填进去的 token 会被拼成 Authorization: Bearer <token>
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .in(SecurityScheme.In.HEADER)
                        .name("Authorization")
                        .description("填登录接口返回的 token 原文即可，不要手动加 Bearer 前缀"));
    }
}
