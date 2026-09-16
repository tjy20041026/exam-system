package com.exam.config;

import com.exam.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Spring MVC 配置：注册拦截器。用实现 WebMvcConfigurer 接口的方式，继承 WebMvcConfigurationSupport 会让自动配置失效。 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                // 白名单式：只拦 /api/**，新接口默认不受保护。反过来「拦所有再排除一堆」的话，
                // 新增免登录路径时忘了同步 exclude 就会 401，而且只在真实调用时才暴露
                .addPathPatterns("/api/**")
                // 登录接口本身不能拦，否则鸡生蛋
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/health/**"
                );
    }
}
