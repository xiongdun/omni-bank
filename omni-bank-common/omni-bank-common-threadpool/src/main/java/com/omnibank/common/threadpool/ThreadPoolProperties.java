package com.omnibank.common.threadpool;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 业务线程池配置（omni.thread-pool）：
 * <pre>
 * omni:
 *   thread-pool:
 *     core-pool-size: 8
 *     max-pool-size: 16
 *     queue-capacity: 200
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "omni.thread-pool")
public class ThreadPoolProperties {

    /** 是否启用自动装配 */
    private boolean enabled = true;

    /** 核心线程数 */
    private int corePoolSize = 8;

    /** 最大线程数 */
    private int maxPoolSize = 16;

    /** 队列容量 */
    private int queueCapacity = 200;

    /** 空闲线程存活秒数 */
    private int keepAliveSeconds = 60;

    /** 线程名前缀 */
    private String threadNamePrefix = "omni-";

    /** 优雅关闭：等待任务完成的秒数 */
    private int awaitTerminationSeconds = 30;
}
