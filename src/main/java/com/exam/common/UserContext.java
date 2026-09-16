package com.exam.common;

import com.exam.common.exception.BizException;

/**
 * 当前请求的登录用户，基于 ThreadLocal。拦截器认证通过后写入，业务代码按需取用。
 * Tomcat 线程池会复用线程，不清理就会串号，所以 afterCompletion 里必须调 {@link #clear()}。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser loginUser) {
        HOLDER.set(loginUser);
    }

    /** 未登录返回 null，require() 则直接抛 401 */
    public static LoginUser get() {
        return HOLDER.get();
    }

    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long getUserId() {
        return require().userId();
    }

    /** 用 remove() 而不是 set(null)，后者不会回收 ThreadLocalMap 里的 Entry */
    public static void clear() {
        HOLDER.remove();
    }
}
