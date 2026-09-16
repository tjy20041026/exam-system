package com.exam.interceptor;

import com.exam.annotation.RequiresRole;
import com.exam.common.LoginUser;
import com.exam.common.Result;
import com.exam.common.ResultCode;
import com.exam.common.UserContext;
import com.exam.enums.UserRole;
import com.exam.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * JWT 认证拦截器。
 *
 * <h3>为什么不用 Spring Security</h3>
 * <p>
 * Spring Security 功能完整，但它的过滤器链、{@code SecurityContext}、
 * 配置类的各种 DSL 引入的复杂度不小 —— 出问题时连异常从哪来都不好找。
 * 本项目需要的只是「校验 token + 查角色」这两件事，
 * 用「拦截器 + jjwt」手写，整套逻辑就是一个 {@code preHandle} 方法，
 * 每一行都能说清楚为什么这么写，排查问题时不需要先理解一个框架。
 *
 * <h3>执行时机</h3>
 * <pre>
 *   DispatcherServlet
 *        ↓
 *   preHandle()        ← 校验 token、放用户进 ThreadLocal、查角色   ★ 本类的主要工作
 *        ↓
 *   Controller / Service   ← 业务代码通过 UserContext.get() 取用
 *        ↓
 *   afterCompletion()  ← 清理 ThreadLocal                        ★ 同样重要，见下
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    /** 约定的请求头，格式为 {@code Authorization: Bearer <token>} */
    private static final String HEADER_NAME = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // ---------- 第 0 步：防御性清理 ----------
        // 正常流程下 afterCompletion 已经清过了，这里再清一次是为了兜底：
        // 万一将来有人加了新的拦截器、或者某个异常路径绕过了 afterCompletion，
        // 这条线程上残留的身份就会被下一个请求读到。
        // 多一次 remove() 的成本可以忽略，换来的是「绝不会串号」的确定性。
        UserContext.clear();

        // ---------- 第 1 步：放行不需要认证的请求 ----------

        // 浏览器发跨域请求前会先发一个 OPTIONS 预检请求，
        // 它按规范【不允许】携带 Authorization 头。如果不放行，
        // 预检就会拿到 401，真正的请求根本发不出去
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // handler 不一定是 HandlerMethod —— 访问静态资源时是 ResourceHttpRequestHandler。
        // 用模式匹配（Java 16+）一步完成「判断类型 + 强转 + 绑定变量」
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // ---------- 第 2 步：取出并解析 token ----------

        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            log.debug("请求未携带 token: {} {}", request.getMethod(), request.getRequestURI());
            return reject(response, ResultCode.UNAUTHORIZED);
        }

        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (ExpiredJwtException e) {
            // 过期单独处理：这是最常见的情况，给前端一个明确的信号，
            // 让它知道该跳登录页了，而不是弹一个含糊的"认证失败"
            log.debug("token 已过期: {}", e.getMessage());
            return reject(response, ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            // 签名不对（被篡改）、格式非法、密钥不匹配等。
            // 这里【不把具体原因返回给客户端】—— 告诉攻击者是"签名错"还是"格式错"
            // 等于免费给了他调试信息。细节只记在服务端日志里
            log.warn("token 校验失败: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return reject(response, ResultCode.UNAUTHORIZED, "登录凭证无效");
        }

        // ---------- 第 3 步：角色校验 ----------
        // 注意顺序：角色校验放在写入 ThreadLocal【之前】。
        // 因为 preHandle 返回 false 时，Spring 不会为本拦截器调用 afterCompletion，
        // 如果先 set 再校验，被拒绝的请求就把身份留在线程上了 —— 正是要避免的事。
        // 让「写入上下文」成为本方法返回 true 之前的最后一个动作，是这里最省心的做法。
        UserRole role = parseRole(claims);

        RequiresRole requiresRole = handlerMethod.getMethodAnnotation(RequiresRole.class);
        if (requiresRole == null) {
            // 方法上没有就找类上的。这种「方法优先、类兜底」的覆盖规则
            // 和 Spring 自己的 @Transactional、@CacheConfig 是一致的
            requiresRole = handlerMethod.getBeanType().getAnnotation(RequiresRole.class);
        }

        if (requiresRole != null && !hasPermission(requiresRole.value(), role)) {
            log.warn("越权访问被拦截: user={}, role={}, uri={}",
                    claims.getSubject(), role, request.getRequestURI());
            return reject(response, ResultCode.FORBIDDEN);
        }

        // ---------- 第 4 步：写入上下文 ----------
        UserContext.set(new LoginUser(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_USERNAME, String.class),
                role));

        return true;
    }

    /**
     * 请求结束后清理 ThreadLocal。
     * <p>
     * Spring 保证这个方法在请求处理完成后<b>一定</b>会执行，
     * 哪怕 Controller 抛了异常也一样（异常会先经 afterCompletion 再往上抛）。
     * 所以这里是放清理逻辑唯一可靠的位置。
     * <p>
     * <b>漏掉这一行的后果</b>：Tomcat 线程池里的线程会被复用，
     * 残留的身份会被下一个请求读到 —— 也就是「张三的请求读到了李四的身份」。
     * 更要命的是它偶发，本地跑一百次都不一定复现，压测时才集中爆发。
     */
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        UserContext.clear();
    }

    // ==================== 私有方法 ====================

    /**
     * 从请求头里取出 token。
     * <p>
     * 顺带兼容一下不带 {@code Bearer } 前缀直接放裸 token 的写法，
     * 因为手工用 curl 调试时很容易漏掉前缀。
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_NAME);
        if (!StringUtils.hasText(header)) {
            return null;
        }
        if (header.startsWith(TOKEN_PREFIX)) {
            // 用 substring 而不是 replace：
            // replace 会把字符串里【所有】出现的位置都换掉，
            // 万一 token 内容里恰好含 "Bearer " 就会把它截坏
            return header.substring(TOKEN_PREFIX.length()).trim();
        }
        return header.trim();
    }

    /**
     * 解析角色，容错处理。
     * <p>
     * token 里的 role 是我们自己签进去的，正常情况下一定合法。
     * 但如果将来枚举改了名字（比如把 STUDENT 改成 USER），
     * 那些<b>改名前签发、还没过期</b>的旧 token 就会解析失败。
     * 这时 {@code valueOf} 会抛 {@code IllegalArgumentException}，
     * 导致所有老用户卡在一个奇怪的 500 上。
     * 返回 null 然后让角色校验去拒绝，表现是一个干净的 401，好排查得多。
     */
    private UserRole parseRole(Claims claims) {
        String roleName = claims.get(CLAIM_ROLE, String.class);
        if (!StringUtils.hasText(roleName)) {
            return null;
        }
        try {
            return UserRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            log.warn("token 中的角色无法识别: {}", roleName);
            return null;
        }
    }

    /** 满足任意一个角色即可通过 */
    private boolean hasPermission(UserRole[] allowed, UserRole actual) {
        if (allowed.length == 0) {
            return true;
        }
        return actual != null && Arrays.asList(allowed).contains(actual);
    }

    /**
     * 拒绝请求：往响应里写一个统一的 JSON 后返回 false 中断后续处理。
     * <p>
     * <b>为什么不能直接 {@code response.sendError(401)}？</b>
     * 那样返回的是 Tomcat 默认的错误页（一段 HTML 或空 body），
     * 前端拿到后按 {@code Result} 结构去解析会直接报 JSON 解析异常，
     * 等于把「未登录」变成了「系统错误」。
     * <p>
     * <b>为什么这里不抛异常交给 GlobalExceptionHandler？</b>
     * 拦截器的异常确实能被全局异常处理器捕获，但那样依赖的是
     * {@code DispatcherServlet} 的异常解析链，绕了一层；
     * 而且拦截器此时还没走到 Controller，直接写回响应是最直白的做法。
     */
    private boolean reject(HttpServletResponse response, ResultCode code) throws Exception {
        return reject(response, code, code.getMessage());
    }

    private boolean reject(HttpServletResponse response, ResultCode code, String message) throws Exception {
        // 同样要清一次：被拒绝的请求不会触发本拦截器的 afterCompletion，
        // 这里不清理的话上下文就留在池化的线程上了
        UserContext.clear();

        // HTTP 状态码统一用 200，业务码放在 body 里。
        // 这是国内主流做法：网关、Nginx 常把非 2xx 当成服务异常去告警和重试，
        // 而且前端 axios 对非 2xx 会走 catch 分支，得在两个地方处理错误
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
        return false;
    }
}
