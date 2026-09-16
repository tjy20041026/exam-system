package com.exam.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档元信息配置。
 * <p>
 * <b>不写这个类会怎样</b>：springdoc 会吐出一份「能跑但很难看」的文档 ——
 * 标题是硬编码的 {@code "OpenAPI definition"}，版本号 {@code "v0"}，
 * 没有作者、没有说明。页面上找不到任何归属信息，面试官打开第一眼看到的是
 * 一个没名字的接口清单。
 * <p>
 * <b>这里配的是「文档的说明书」，不是业务代码</b>，但它决定了别人打开文档的第一印象，
 * 所以和业务代码一样值得写清楚。
 */
@Configuration
public class OpenApiConfig {

    /**
     * 文档全局元信息 + 安全方案。
     * <p>
     * <b>注意：安全方案必须挂在 OpenAPI 对象上，不能单独声明一个 Components bean。</b>
     * springdoc 只会去容器里找 {@code OpenAPI} 类型的 bean，一个孤立的 {@code Components}
     * bean 就是个没人读的普通对象 —— 现象是文档能打开、info 也正常，
     * 但 {@code securitySchemes} 是空的 {@code {}}，Authorize 按钮压根不出现。
     * 这种「静默失效」不报错、不警告，只能靠打开 {@code /v3/api-docs} 核对 JSON 才能发现。
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
                        // 邮箱和 git 配置里的一致，面试前可自行改成真实联系方式
                        .contact(new Contact()
                                .name("唐靖祎")
                                .email("tangjingyi@example.com")))
                .components(examComponents());
    }

    /**
     * JWT 的安全方案 —— 各控制器上的 {@code @SecurityRequirement(name = "Authorization")}
     * 只是<b>引用</b>一个名字，方案本身得在这里定义出来。
     * <p>
     * 名字必须和注解里写的字符串完全一致（这里是 {@code "Authorization"}），
     * 对不上就是引用了一个不存在的东西 —— 编译不报错、启动不报错，
     * 只是页面上<b>没有 Authorize 按钮</b>，需要登录的接口一个都调不通。
     */
    private Components examComponents() {
        return new Components().addSecuritySchemes("Authorization",
                new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        // 必须是 "bearer"：填进去的 token 会被拼成 `Authorization: Bearer <token>`
                        .scheme("bearer")
                        // "JWT" 只是给页面上的提示文案用的，不影响实际行为
                        .bearerFormat("JWT")
                        .in(SecurityScheme.In.HEADER)
                        .name("Authorization")
                        .description("填登录接口返回的 token 原文即可，不要手动加 Bearer 前缀"));
    }
}
