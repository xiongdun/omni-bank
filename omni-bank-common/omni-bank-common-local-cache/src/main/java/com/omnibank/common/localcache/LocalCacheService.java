package com.omnibank.common.localcache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 本地缓存服务：基于 Caffeine 的进程内缓存。
 * 默认策略：写入后 30 分钟过期、单实例最多 10000 条。
 * TODO: 后续按需扩展多缓存空间与自定义过期策略。
 * 约定：服务应用启动类需位于 com.omnibank 包下，使本模块 Bean 被组件扫描。
 */
@Component
public class LocalCacheService {

    private final Cache<String, Object> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(10_000)
            .build();

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        Object value = cache.getIfPresent(key);
        return value == null ? Optional.empty() : Optional.of((T) value);
    }

    public void put(String key, Object value) {
        cache.put(key, value);
    }

    public void evict(String key) {
        cache.invalidate(key);
    }

    public void clear() {
        cache.invalidateAll();
    }
}
