package com.omnibank.common.lock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解：标注在需要互斥执行的方法上。
 * key 支持 SpEL 表达式引用方法参数；leaseTime <= 0 时启用 Redisson 看门狗自动续期。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /** 锁键 SpEL 表达式，如 "#req.accountId" */
    String key();

    /** 锁键前缀，默认取方法全限定名 */
    String prefix() default "";

    /** 获取锁最大等待时间，小于等于 0 表示不等待直接失败 */
    long waitTime() default 3;

    /** 持有锁时间，小于等于 0 表示启用看门狗自动续期 */
    long leaseTime() default -1;

    /** 时长单位 */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
