package com.exam.common;

import com.exam.common.exception.BizException;

/**
 * 当前请求的登录用户上下文（基于 ThreadLocal）。
 * <p>
 * <b>为什么需要它？</b>
 * <p>
 * 拦截器解析出用户身份后，Controller 和 Service 都需要用。
 * 最直接的做法是一层层往下传参：{@code Controller(id, user)} → {@code Service(id, user)} → …，
 * 但业务方法会被这个跟业务无关的参数污染，而且只要中间漏传一层就断了。
 * ThreadLocal 相当于给「当前这个请求」开了一个只有它能看到的储物柜，
 * 谁需要谁自己去取。
 *
 * <h3>⚠️ 这里有个必须记住的坑：一定要清理</h3>
 * <p>
 * Tomcat 处理请求用的是<b>线程池</b>，线程是<b>复用</b>的。
 * 假设请求 A 设置了 {@code user=张三} 却忘了清理，那么下一个恰好被分配到同一条线程的请求 B，
 * 如果它是个不需要登录的接口（比如登录接口本身），就会读到张三的身份。
 * 更糟的是这是个<b>偶发</b>问题 —— 本地测十次都不复现，一上并发就出事。
 * <p>
 * 所以：{@code JwtInterceptor.afterCompletion()} 里<b>必须</b>调用 {@link #clear()}。
 * Spring 保证 {@code afterCompletion} 在请求结束后一定执行（哪怕业务抛了异常），
 * 这是放置清理逻辑最可靠的位置。
 *
 * @see LoginUser
 */
public final class UserContext {

    /**
     * ThreadLocal 的 key 就是当前线程自己，所以一个静态常量就够了。
     * <p>
     * 声明为 {@code final} 且私有，防止外部乱动这个引用。
     */
    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    /** 工具类不允许被实例化 */
    private UserContext() {
    }

    /** 绑定当前登录用户。由拦截器在认证通过后调用 */
    public static void set(LoginUser loginUser) {
        HOLDER.set(loginUser);
    }

    /**
     * 获取当前登录用户。
     *
     * @return 未登录时返回 {@code null}。调用方需要自行判空，
     *         或者改用下面的 {@link #require()} / {@link #getUserId()}
     */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /**
     * 获取当前登录用户，未登录直接抛异常。
     * <p>
     * 业务代码里用这个更省事：不需要在每个方法开头写 {@code if (user == null)} ——
     * 正常情况下拦截器已经保证了登录，真要是走到这里还是 null，
     * 说明是代码写错了（比如新加的接口漏配了拦截路径），应该尽早炸出来而不是继续往下跑。
     */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    /** 取当前用户 ID，最常用的一个快捷方法 */
    public static Long getUserId() {
        return require().userId();
    }

    /**
     * 清理。
     * <p>
     * 注意用的是 {@code remove()} 而不是 {@code set(null)}：
     * {@code set(null)} 只是把值置空，那个 ThreadLocalMap 的 Entry 还在，
     * key 的弱引用也不会被回收，长期运行会积累垃圾。
     * {@code remove()} 才是真正的清理。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
