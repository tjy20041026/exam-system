package com.exam.annotation;

import com.exam.enums.UserRole;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口所需的角色。由 {@code JwtInterceptor} 读取并校验。
 *
 * <h3>用法</h3>
 * <pre>
 * // 只有教师和管理员能建题
 * {@code @RequiresRole({UserRole.TEACHER, UserRole.ADMIN})}
 * {@code @PostMapping("/api/questions")}
 * public Result&lt;?&gt; create(...) { ... }
 * </pre>
 *
 * <h3>为什么用注解，而不是在方法里写 if</h3>
 * <p>
 * 直接写 {@code if (!user.isTeacher()) throw ...} 也能跑，但有两个问题：
 * <ul>
 *   <li><b>会漏</b>：每加一个接口都得记得写一行，漏一个就是越权漏洞，而且很难在测试中发现</li>
 *   <li><b>看不出全貌</b>：想知道"哪些接口是教师专属的"，
 *       得把 Controller 一个个翻过去；用注解的话搜一下 {@code @RequiresRole} 就全出来了</li>
 * </ul>
 * 把权限声明变成<b>接口签名的一部分</b>，这件事本身就是文档，而且不写默认就是拒绝，
 * 符合安全设计里「默认拒绝」的原则。
 *
 * <h3>关于注解的元信息</h3>
 * <ul>
 *   <li>{@code @Target({METHOD, TYPE})} —— 标在方法上表示只管这一个接口；
 *       标在类上表示这个 Controller 下的所有接口都适用，
 *       方法上的注解优先。这种「就近覆盖」的规则和 Spring 的事务、缓存注解是一致的</li>
 *   <li>{@code @Retention(RUNTIME)} —— <b>必须</b>是 RUNTIME。
 *       默认的 CLASS 级别在编译后就被丢弃了，运行期反射读不到，注解会静默失效。
 *       这是写自定义注解最常见的坑</li>
 *   <li>{@code @Documented} —— 让这个注解出现在生成的 Javadoc 里</li>
 * </ul>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRole {

    /**
     * 允许访问的角色，<b>满足其中任意一个即可</b>（是「或」不是「与」）。
     * <p>
     * 不设默认值，强制使用方明确写出允许谁访问 ——
     * 有默认值的话很容易被漏写，而漏写的默认值如果是「所有人」就是安全问题。
     */
    UserRole[] value();
}
