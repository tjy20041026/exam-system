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
 * JWT 认证拦截器。preHandle 里校验 token、查角色并写入上下文，afterCompletion 负责清理。
 * <p>
 * 没用 Spring Security：这里只要「校验 token + 查角色」两件事，拦截器加 jjwt 手写就一个
 * preHandle，出问题时不用先去理解一整套过滤器链和 SecurityContext。
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

        // 兜底再清一次：正常流程 afterCompletion 已经清过，但万一有异常路径绕过它，
        // 残留在池化线程上的身份就会被下一个请求读到
        UserContext.clear();

        // OPTIONS 预检请求按规范不允许携带 Authorization 头，不放行的话预检直接 401
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            log.debug("请求未携带 token: {} {}", request.getMethod(), request.getRequestURI());
            return reject(response, ResultCode.UNAUTHORIZED);
        }

        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (ExpiredJwtException e) {
            // 过期是最常见的情况，给前端一个明确的信号让它跳登录页
            log.debug("token 已过期: {}", e.getMessage());
            return reject(response, ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            // 签名不对、格式非法、密钥不匹配等。具体原因不返回给客户端，只记服务端日志
            log.warn("token 校验失败: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return reject(response, ResultCode.UNAUTHORIZED, "登录凭证无效");
        }

        // 角色校验必须放在写入 ThreadLocal 之前：preHandle 返回 false 时 Spring 不会为本拦截器
        // 调用 afterCompletion，先 set 的话被拒绝的请求就把身份留在线程上了
        UserRole role = parseRole(claims);

        RequiresRole requiresRole = handlerMethod.getMethodAnnotation(RequiresRole.class);
        if (requiresRole == null) {
            requiresRole = handlerMethod.getBeanType().getAnnotation(RequiresRole.class);
        }

        if (requiresRole != null && !hasPermission(requiresRole.value(), role)) {
            log.warn("越权访问被拦截: user={}, role={}, uri={}",
                    claims.getSubject(), role, request.getRequestURI());
            return reject(response, ResultCode.FORBIDDEN);
        }

        UserContext.set(new LoginUser(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_USERNAME, String.class),
                role));

        return true;
    }

    /**
     * 请求结束后清理 ThreadLocal。Spring 保证这个方法一定执行，哪怕 Controller 抛了异常，
     * 所以清理放这里最可靠，不清理的后果见 {@link UserContext}。
     */
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        UserContext.clear();
    }

    /** 取出 token，顺带兼容裸 token —— 手工用 curl 调试时容易漏掉 Bearer 前缀 */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_NAME);
        if (!StringUtils.hasText(header)) {
            return null;
        }
        if (header.startsWith(TOKEN_PREFIX)) {
            // 用 substring 而不是 replace，后者会把 token 内容里出现的位置也换掉
            return header.substring(TOKEN_PREFIX.length()).trim();
        }
        return header.trim();
    }

    /** 解析角色并容错：枚举改名后，改名前签发还没过期的旧 token 会解析失败，返回 null 交给校验去拒 */
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
     * 拒绝请求：往响应里写一个统一的 JSON 后返回 false。
     * <p>
     * 不直接 {@code response.sendError(401)}，那样返回的是 Tomcat 的错误页，
     * 前端按 {@code Result} 结构解析会报 JSON 异常。
     */
    private boolean reject(HttpServletResponse response, ResultCode code) throws Exception {
        return reject(response, code, code.getMessage());
    }

    private boolean reject(HttpServletResponse response, ResultCode code, String message) throws Exception {
        // 被拒绝的请求不会触发本拦截器的 afterCompletion，这里也得清一次
        UserContext.clear();

        // HTTP 状态码统一用 200，业务码放在 body 里 —— 前端 axios 对非 2xx 会走 catch 分支，
        // 那样就得在两个地方处理错误
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
        return false;
    }
}
