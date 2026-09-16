package com.exam.annotation;

import com.exam.enums.UserRole;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口所需的角色，由 JwtInterceptor 读取校验。
 * value 故意不给默认值：有默认值就可能漏写，默认放行等于越权，现在漏写直接编译不过。
 * Retention 必须是 RUNTIME，CLASS 级别运行时反射读不到，注解会静默失效。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRole {

    /** 满足其中任意一个角色即可。 */
    UserRole[] value();
}
