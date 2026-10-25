package com.omnibank.common.threadpool;

import com.alibaba.ttl.TtlRunnable;
import com.alibaba.ttl.threadpool.TtlExecutors;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 业务线程池自动装配：引依赖即生效，boot 端零扫描成本。
 *
 * 特性：
 * 1. 参数经 omni.thread-pool 配置（见 {@link ThreadPoolProperties}）
 * 2. TaskDecorator 使用 TTL 装饰，提交到线程池的任务可传递 TransmittableThreadLocal 上下文
 * 3. 优雅关闭：等待队列任务完成后销毁
 * 4. 拒绝策略 CallerRuns：队列满时由调用线程执行，兜底不丢任务
 *
 * 业务自定义：注册自己的 ThreadPoolTaskExecutor Bean 即可覆盖（ConditionalOnMissingBean）。
 */
@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
@ConditionalOnProperty(prefix = "omni.thread-pool", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ThreadPoolAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ThreadPoolTaskExecutor.class)
    public ThreadPoolTaskExecutor threadPoolTaskExecutor(ThreadPoolProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        // TTL 装饰：异步任务传递上下文（用户/租户/链路信息）
        executor.setTaskDecorator(TtlRunnable::get);
        // 优雅关闭：等待任务完成后销毁
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());
        // 拒绝策略：队列满时由调用者线程执行，兜底不丢任务（银行场景任务不可丢）
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // 统一经 TTL 包装对外暴露，保证 submit/execute 全链路上下文传递
        return executor;
    }

    /** 对外暴露 TTL 包装后的执行器（CompletableFuture 等场景使用） */
    @Bean
    @ConditionalOnMissingBean(name = "ttlExecutor")
    public java.util.concurrent.Executor ttlExecutor(ThreadPoolTaskExecutor executor) {
        return TtlExecutors.getTtlExecutor(executor);
    }
}
