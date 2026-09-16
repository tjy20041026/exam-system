package com.exam.util;

import com.exam.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 工具类。
 * <p>
 * 签名保证的是「改不了」而不是「看不见」：payload 只是 Base64，去 jwt.io 粘一下就能解出来，
 * 所以不能往里放密码这类敏感信息。
 * <p>
 * 无状态也意味着没法主动失效 —— 服务端什么都不存，用户退出登录或管理员封号之后，
 * 已发出的 token 在过期前依然有效。
 */
@Slf4j
@Component
public class JwtUtil {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    /** HS256 要求密钥至少 32 字节 */
    private static final int MIN_SECRET_BYTES = 32;

    /** 签名密钥。构造一次缓存起来，没必要每个请求重新推导 */
    private final SecretKey secretKey;

    private final long expireMillis;

    public JwtUtil(@Value("${exam.jwt.secret}") String secret,
                   @Value("${exam.jwt.expire-minutes}") long expireMinutes) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);

        // 启动时就校验长度，省得等签名时抛 WeakKeyException，堆栈指向签名那行还要绕一圈定位到配置
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "exam.jwt.secret 长度不足：HS256 要求至少 " + MIN_SECRET_BYTES
                            + " 字节（UTF-8 编码后），当前只有 " + keyBytes.length + " 字节");
        }

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expireMillis = Duration.ofMinutes(expireMinutes).toMillis();

        log.info("JwtUtil 初始化完成，token 有效期 {} 分钟", expireMinutes);
    }

    /** 签发 token。userId 放在标准字段 {@code sub} 里 */
    public String generate(Long userId, String username, UserRole role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                // 存枚举的 name() 而不是中文 label，label 哪天改文案就全乱套了
                .claim(CLAIM_ROLE, role == null ? null : role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                // 明确指定 HS256，不指定的话库会按密钥长度自己挑
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并校验 token。只抛 jjwt 自己的异常，翻译成哪种业务含义由调用方决定 ——
     * 工具类不管 HTTP 语义，将来在定时任务或消息队列里用也不会别扭。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** token 有效期（秒），返回给前端方便它提前刷新 */
    public long getExpireSeconds() {
        return expireMillis / 1000;
    }
}
