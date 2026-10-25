package com.omnibank.common.idempotent.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 幂等注解：标注在需要防重复提交的方法上。
 * key 支持 SpEL 表达式引用方法参数，默认在锁定时长内相同 key 只允许执行一次。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /** 幂等键 SpEL 表达式，如 "#orderNo" 或 "#req.orderId" */
    String key();

    /** 幂等键前缀，默认取方法全限定名 */
    String prefix() default "";

    /** 幂等锁定时长 */
    long ttl() default 10;

    /** 时长单位 */
    TimeUnit timeUnit() default TimeUnit.MINUTES;
}
