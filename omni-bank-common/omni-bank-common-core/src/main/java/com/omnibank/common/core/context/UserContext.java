package com.omnibank.common.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 用户上下文（TransmittableThreadLocal 实现）：
 * 1. 登录态解析处写入（如认证 Filter），请求链路任意位置读取，请求结束必须 clear
 * 2. 经 TTL 传递至业务线程池（common-threadpool 已装配 TaskDecorator=TtlRunnable），异步任务内可直接读取
 * 3. 审计字段填充（common-mybatis）自动取当前操作人
 */
public final class UserContext {

    private static final TransmittableThreadLocal<CurrentUser> CONTEXT = new TransmittableThreadLocal<>();

    private UserContext() {
    }

    /** 写入当前用户 */
    public static void set(CurrentUser user) {
        CONTEXT.set(user);
    }

    /** 读取当前用户（未登录/未写入时返回 null） */
    public static CurrentUser get() {
        return CONTEXT.get();
    }

    /** 当前用户 ID（未登录返回 null） */
    public static Long getUserId() {
        CurrentUser user = get();
        return user == null ? null : user.getUserId();
    }

    /** 当前登录账号（未登录返回 null） */
    public static String getUsername() {
        CurrentUser user = get();
        return user == null ? null : user.getUsername();
    }

    /** 请求结束清理（防线程复用串号） */
    public static void clear() {
        CONTEXT.remove();
    }
}
