package com.omnibank.common.idempotent.aspect;

import com.omnibank.common.core.exception.BizException;
import com.omnibank.common.core.result.GlobalErrorCode;
import com.omnibank.common.idempotent.annotation.Idempotent;
import com.omnibank.common.remotecache.RemoteCacheService;
import java.lang.reflect.Method;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.StandardReflectionParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

/**
 * 幂等切面：以 Redis setIfAbsent（原子操作）实现窗口期内防重。
 * 约定：服务应用启动类需位于 com.omnibank 包下，使本模块 Bean 被组件扫描。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private static final SpelExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new StandardReflectionParameterNameDiscoverer();

    private static final String KEY_TEMPLATE = "omni:idempotent:%s:%s";

    private final RemoteCacheService remoteCacheService;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String cacheKey = buildKey(joinPoint, idempotent);
        boolean acquired = remoteCacheService.putIfAbsent(cacheKey, "1", resolveTtl(idempotent));
        if (!acquired) {
            log.warn("命中幂等拦截: key={}", cacheKey);
            throw new BizException(GlobalErrorCode.DUPLICATE_REQUEST);
        }
        return joinPoint.proceed();
    }

    private String buildKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String prefix = idempotent.prefix().isBlank()
                ? method.getDeclaringClass().getName() + "#" + method.getName()
                : idempotent.prefix();
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                null, method, joinPoint.getArgs(), PARAMETER_NAME_DISCOVERER);
        String key = EXPRESSION_PARSER.parseExpression(idempotent.key()).getValue(context, String.class);
        return String.format(KEY_TEMPLATE, prefix, key);
    }

    private Duration resolveTtl(Idempotent idempotent) {
        return Duration.of(idempotent.ttl(), idempotent.timeUnit().toChronoUnit());
    }
}
