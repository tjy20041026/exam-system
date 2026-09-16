package com.exam.config;

import com.exam.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置：注册拦截器。
 * <p>
 * {@code WebMvcConfigurer} 是 Spring Boot 提供的<b>扩展点</b>接口，
 * 它里面所有方法都有默认空实现，你只需要覆盖关心的那几个。
 * Spring Boot 会自动找到实现了这个接口的 Bean 并把配置合并进去。
 * <p>
 * 早期版本要继承 {@code WebMvcConfigurationSupport}，但那样会
 * <b>导致 Spring Boot 的 MVC 自动配置整体失效</b>（因为它以为你要全手动接管），
 * 静态资源、消息转换器都得自己重配一遍。现在统一用实现接口的方式。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                // ---------- 只拦截 /api/** ----------
                // 这是本配置最关键的一行。用 addPathPatterns 白名单式地
                // 只拦业务接口，而不是「拦所有再排除一堆」。
                //
                // 后者是更常见的写法，但风险在于：新增一个不需要登录的路径
                // （比如将来接入支付回调 /api/pay/notify）时，
                // 必须记得同步加到 exclude 列表里，忘了就会 401，而且只在真实回调时才暴露。
                //
                // 白名单式则相反：新接口默认【不】受保护，需要保护就放在 /api/** 下。
                // 两种思路各有取舍，但对本项目来说，
                // 接口文档 /v3/api-docs、/doc.html、静态资源天然都在 /api 之外，
                // 不用写一长串排除规则，配置干净很多。
                .addPathPatterns("/api/**")
                // ---------- 排除登录接口本身 ----------
                // 不排除的话，登录请求也要带 token 才能登录 —— 鸡生蛋问题
                .excludePathPatterns(
                        "/api/auth/login",
                        // 健康检查是给监控系统用的，监控不带 token。
                        // 而且这个接口如果不放行，运维排查问题时反而拿不到信息
                        "/api/health/**"
                );
    }
}
