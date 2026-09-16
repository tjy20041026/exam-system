package com.exam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密器配置。
 * <p>
 * 注意这里的依赖是 {@code spring-security-crypto} —— 一个纯工具 jar，
 * <b>不会</b>激活 Spring Security 的 Web 过滤器链（那需要 spring-boot-starter-security）。
 * 所以我们只是借用了它的 BCrypt 实现，没有引入 Spring Security 的复杂度。
 * <p>
 * <b>为什么用 BCrypt 而不是 MD5 / SHA-256？</b>
 * <ul>
 *   <li>MD5 和 SHA-256 是「快」哈希，设计目标就是算得快 —— 这对密码存储是缺点。
 *       现代显卡每秒能算几十亿次 MD5，8 位密码几小时就能暴力破解完</li>
 *   <li>BCrypt 故意设计得慢（可调工作因子），且每次加密都用随机盐，
 *       同样的密码每次加密结果都不同，彩虹表攻击失效</li>
 *   <li>BCrypt 的哈希值本身已包含盐和成本因子，所以数据库只需一个字段存密文，
 *       不必单独存盐</li>
 * </ul>
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // strength=10 是默认值，表示做 2^10 次迭代。
        // 数值越大越安全但越慢，10 在"安全"和"登录响应速度"之间是公认的平衡点。
        return new BCryptPasswordEncoder();
    }
}
