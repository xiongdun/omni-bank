package com.omnibank.common.lock.aspect;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.common.core.result.GlobalErrorCode;
import com.omnibank.common.lock.annotation.DistributedLock;
import java.lang.reflect.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.StandardReflectionParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

/**
 * 分布式锁切面：基于 Redisson RLock 的 tryLock 实现互斥。
 * 约定：服务应用启动类需位于 com.omnibank 包下，使本模块 Bean 被组件扫描。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private static final SpelExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new StandardReflectionParameterNameDiscoverer();

    private static final String KEY_TEMPLATE = "omni:lock:%s:%s";

    private final RedissonClient redissonClient;

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = buildKey(joinPoint, distributedLock);
        RLock lock = redissonClient.getLock(lockKey);
        long waitTime = Math.max(0, distributedLock.waitTime());
        boolean acquired = distributedLock.leaseTime() > 0
                ? lock.tryLock(waitTime, distributedLock.leaseTime(), distributedLock.timeUnit())
                : lock.tryLock(waitTime, distributedLock.timeUnit());
        if (!acquired) {
            log.warn("获取分布式锁失败: key={}", lockKey);
            throw new BizException(GlobalErrorCode.SERVICE_UNAVAILABLE, "操作正在处理中，请稍后重试");
        }
        try {
            return joinPoint.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String buildKey(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String prefix = distributedLock.prefix().isBlank()
                ? method.getDeclaringClass().getName() + "#" + method.getName()
                : distributedLock.prefix();
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                null, method, joinPoint.getArgs(), PARAMETER_NAME_DISCOVERER);
        String key = EXPRESSION_PARSER.parseExpression(distributedLock.key()).getValue(context, String.class);
        return String.format(KEY_TEMPLATE, prefix, key);
    }
}
