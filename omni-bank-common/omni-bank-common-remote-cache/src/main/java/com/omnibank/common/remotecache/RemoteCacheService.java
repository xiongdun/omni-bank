package com.omnibank.common.remotecache;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 远程缓存服务：基于 Redisson 的 Redis 常用操作封装。
 * RedissonClient 由 redisson-spring-boot-starter 自动装配，应用需配置 Redis 连接信息。
 * 约定：服务应用启动类需位于 com.omnibank 包下，使本模块 Bean 被组件扫描。
 */
@Component
@RequiredArgsConstructor
public class RemoteCacheService {

    private final RedissonClient redissonClient;

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        T value = bucket.get();
        return value == null ? Optional.empty() : Optional.of(value);
    }

    /** 写入缓存，ttl 为 null 表示永不过期 */
    public void put(String key, Object value, Duration ttl) {
        RBucket<Object> bucket = redissonClient.getBucket(key);
        if (ttl == null) {
            bucket.set(value);
        } else {
            bucket.set(value, ttl);
        }
    }

    public Boolean delete(String key) {
        return redissonClient.getBucket(key).delete();
    }

    /** 仅当 key 不存在时写入（原子操作），用于简单互斥场景 */
    public boolean putIfAbsent(String key, Object value, Duration ttl) {
        RBucket<Object> bucket = redissonClient.getBucket(key);
        return bucket.setIfAbsent(value, ttl);
    }
}
