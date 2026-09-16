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
 *
 * <h3>JWT 是什么，为什么用它</h3>
 * <p>
 * 传统登录靠 Session：服务端存一份「sessionId → 用户」的映射，客户端只拿 sessionId。
 * 问题在于这份映射存在<b>服务端内存</b>里，一旦部署两台机器，用户在 A 机器登录、
 * 下一个请求被负载均衡打到 B 机器，B 机器不认识这个 sessionId，就得重新登录。
 * 解决办法是把 session 抽出来存 Redis（这本身没问题，也是常见做法），
 * 但那就多了一个必须高可用的组件。
 * <p>
 * JWT 换了思路：把用户信息<b>签名后交给客户端自己保管</b>。
 * 服务端不需要存任何东西，拿到 token 用密钥验一下签名就知道有没有被篡改。
 * 这叫<b>无状态</b>，天然支持多实例部署 —— 加机器不用同步任何数据。
 * <p>
 * 注意措辞是「签名」不是「加密」：JWT 的 payload 只是 Base64 编码，
 * <b>任何人都能解出里面的内容</b>（去 jwt.io 粘一下就能看）。
 * 签名保证的是「改不了」，不是「看不见」。
 * 所以<b>绝不能往 payload 里放密码、手机号之类的敏感信息</b>。
 *
 * <h3>JWT 的固有缺点：没法主动失效</h3>
 * <p>
 * 因为服务端不存状态，所以<b>没法主动让一个 token 失效</b>。
 * 用户点了「退出登录」，或者管理员封了某个账号，已经发出去的 token
 * 在过期之前依然有效。业界标准的解法是引入 Redis 黑名单：
 * 退出时把该 token（或它的 jti）写进 Redis 并设置剩余 TTL，
 * 拦截器每次校验时多查一次黑名单。
 * <p>
 * 这恰好说明一个道理：<b>「无状态」不是免费的，它把复杂度从「存储」转移到了「失效控制」上</b>。
 * 本项目为了保持简单没有实现黑名单，但这是必须知道的一环。
 */
@Slf4j
@Component
public class JwtUtil {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    /** HS256 算法要求密钥至少 256 位，即 32 字节 */
    private static final int MIN_SECRET_BYTES = 32;

    /**
     * 签名密钥。
     * <p>
     * 用 {@link SecretKey} 而不是每次签名时现算：
     * 密钥是<b>不可变</b>的，构造一次缓存起来即可，没必要每个请求都重新推导一遍。
     */
    private final SecretKey secretKey;

    /** token 有效期（毫秒） */
    private final long expireMillis;

    /**
     * @param secret        签名密钥，来自配置。生产环境应通过环境变量注入，不写死在代码库里
     * @param expireMinutes 有效期（分钟）
     */
    public JwtUtil(@Value("${exam.jwt.secret}") String secret,
                   @Value("${exam.jwt.expire-minutes}") long expireMinutes) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);

        // 提前校验而不是等签名时报错。
        // jjwt 在密钥长度不足时抛的是 WeakKeyException，堆栈指向签名那行，
        // 排查时要绕一圈才能定位到是配置写短了。启动时直接失败最省事。
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "exam.jwt.secret 长度不足：HS256 要求至少 " + MIN_SECRET_BYTES
                            + " 字节（UTF-8 编码后），当前只有 " + keyBytes.length + " 字节");
        }

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expireMillis = Duration.ofMinutes(expireMinutes).toMillis();

        log.info("JwtUtil 初始化完成，token 有效期 {} 分钟", expireMinutes);
    }

    /**
     * 签发 token。
     *
     * @param userId   用户 ID。放在标准字段 {@code sub} 里
     * @param username 登录账号
     * @param role     角色。拦截器要靠它做权限判断
     */
    public String generate(Long userId, String username, UserRole role) {
        Date now = new Date();
        return Jwts.builder()
                // sub（subject）是 JWT 的标准字段，语义就是「这个 token 代表谁」
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                // 存枚举的 name() 而不是中文 label：
                // name 是代码里的标识，稳定不变；label 是给人看的，哪天改成"老师"就全乱套了
                .claim(CLAIM_ROLE, role == null ? null : role.name())
                // iat / exp 也是标准字段，库会自动填进 payload
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                // 明确指定 HS256。不指定的话库会根据密钥长度自己挑，行为不够确定
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并校验 token。
     * <p>
     * <b>这个方法刻意不抛业务异常</b>，只把 jjwt 自己的异常（{@code ExpiredJwtException}、
     * {@code SignatureException} 等）往外扔，由调用方 {@code JwtInterceptor} 决定
     * 翻译成哪种业务含义。
     * <p>
     * 理由是分层：工具类只负责「技术上能不能解析」，
     * 「过期了该返回 401 还是提示重新登录」是 Web 层的决定。
     * 如果这里直接抛 {@code BizException}，工具类就绑死在 HTTP 语义上了，
     * 将来要在定时任务或消息队列里用这个类会很别扭。
     *
     * @return 校验通过后的 payload
     * @throws io.jsonwebtoken.JwtException 签名不对、已过期、格式非法等
     */
    public Claims parse(String token) {
        return Jwts.parser()
                // 0.12.x 的新 API：verifyWith 替代了老版本的 setSigningKey。
                // 方法名从「设置密钥」变成「用它来验签」，语义更准确
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** token 有效期（秒），用于返回给前端方便它提前刷新 */
    public long getExpireSeconds() {
        return expireMillis / 1000;
    }
}
