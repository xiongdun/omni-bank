package com.omnibank.common.threadpool;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 线程池自动装配验证：bean 存在、参数绑定、开关关闭。
 */
class ThreadPoolAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ThreadPoolAutoConfiguration.class));

    @Test
    void shouldRegisterExecutorByDefault() {
        runner.withPropertyValues("omni.thread-pool.core-pool-size=3", "omni.thread-pool.thread-name-prefix=test-")
                .run(context -> {
                    assertThat(context).hasSingleBean(ThreadPoolTaskExecutor.class);
                    assertThat(context).hasBean("ttlExecutor");
                    ThreadPoolTaskExecutor executor = context.getBean(ThreadPoolTaskExecutor.class);
                    assertThat(executor.getCorePoolSize()).isEqualTo(3);
                    assertThat(executor.getThreadNamePrefix()).isEqualTo("test-");
                });
    }

    @Test
    void shouldBeDisabledWhenEnabledIsFalse() {
        runner.withPropertyValues("omni.thread-pool.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ThreadPoolTaskExecutor.class);
                    assertThat(context).doesNotHaveBean(Executor.class);
                });
    }

    @Test
    void shouldAllowUserDefinedExecutorOverride() {
        runner.withBean(ThreadPoolTaskExecutor.class)
                .run(context -> {
                    ThreadPoolTaskExecutor userDefined = context.getBean(ThreadPoolTaskExecutor.class);
                    assertThat(userDefined.getCorePoolSize()).isEqualTo(1);
                });
    }
}
