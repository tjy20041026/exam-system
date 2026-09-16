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

                                ## 怎么用这份文档

                                1. 先调 `POST /api/auth/login` 拿到 `token`（三个测试账号见下）
                                2. 点右上角 **Authorize**，把 token 填进去（**不用**手动加 `Bearer ` 前缀，这里会自动补）
                                3. 之后所有需要登录的接口都会自动带上 `Authorization` 头

                                ## 测试账号

                                | 账号 | 密码 | 角色 |
                                |---|---|---|
                                | `admin` | `Admin@123` | 管理员 |
                                | `teacher01` | `Teacher@123` | 教师 |
                                | `student01` | `Student@123` | 学生 |

                                ## 建议的演示顺序

                                `00-环境自检` → `01-认证` → `03-题库` → `04-试卷` → `05-考试（学生端）`

                                > 考试流程必须用**学生**账号；教师账号调考试接口会被 403 拒绝。
                                > 学生端返回的题目**不含标准答案**，交卷后也不返回 —— 防止先交卷的泄题给后考的。
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
