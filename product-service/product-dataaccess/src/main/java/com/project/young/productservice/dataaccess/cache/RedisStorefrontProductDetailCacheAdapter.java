package com.project.young.productservice.dataaccess.cache;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.port.output.StorefrontProductDetailCachePort;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.dataaccess.config.StorefrontProductCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Repository
@ConditionalOnProperty(prefix = "product-service.storefront-cache", name = "enabled", havingValue = "true")
@Slf4j
public class RedisStorefrontProductDetailCacheAdapter implements StorefrontProductDetailCachePort {

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                else
                    return 0
                end
            """,
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final StorefrontProductCacheProperties properties;
    private final ReadProductDetailViewJsonMapper jsonMapper;

    public RedisStorefrontProductDetailCacheAdapter(
            StringRedisTemplate stringRedisTemplate,
            StorefrontProductCacheProperties properties,
            ReadProductDetailViewJsonMapper jsonMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<ReadProductDetailView> findCached(ProductId productId) {
        String json = stringRedisTemplate.opsForValue().get(cacheKey(productId));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(jsonMapper.fromJson(json));
    }

    @Override
    public void put(ProductId productId, ReadProductDetailView view) {
        stringRedisTemplate.opsForValue().set(
                cacheKey(productId),
                jsonMapper.toJson(view),
                Duration.ofSeconds(resolveTtlSeconds())
        );
    }

    @Override
    public void evict(ProductId productId) {
        stringRedisTemplate.delete(cacheKey(productId));
    }

    @Override
    public Optional<ReadProductDetailView> getOrLoad(
            ProductId productId,
            Supplier<Optional<ReadProductDetailView>> loader
    ) {
        Optional<ReadProductDetailView> cached = findCached(productId);
        if (cached.isPresent()) {
            return cached;
        }

        String lockKey = lockKey(productId);
        String lockToken = UUID.randomUUID().toString();
        boolean acquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockToken,
                Duration.ofSeconds(properties.getLockTtlSeconds())
        ));

        if (acquired) {
            try {
                Optional<ReadProductDetailView> doubleCheck = findCached(productId);
                if (doubleCheck.isPresent()) {
                    return doubleCheck;
                }
                Optional<ReadProductDetailView> loaded = loader.get();
                loaded.ifPresent(view -> put(productId, view));
                return loaded;
            } finally {
                releaseLock(lockKey, lockToken);
            }
        }

        for (int attempt = 0; attempt < properties.getLockWaitRetries(); attempt++) {
            sleep(properties.getLockWaitMillis());
            Optional<ReadProductDetailView> retryHit = findCached(productId);
            if (retryHit.isPresent()) {
                return retryHit;
            }
        }

        log.debug("Storefront PDP cache lock wait exhausted for product {}", productId.getValue());
        Optional<ReadProductDetailView> loaded = loader.get();
        loaded.ifPresent(view -> put(productId, view));
        return loaded;
    }

    private void releaseLock(String lockKey, String lockToken) {
        stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), lockToken);
    }

    private String cacheKey(ProductId productId) {
        return properties.getKeyPrefix() + "detail:" + productId.getValue();
    }

    private String lockKey(ProductId productId) {
        return properties.getKeyPrefix() + "lock:" + productId.getValue();
    }

    private long resolveTtlSeconds() {
        long jitterBound = Math.max(0, properties.getTtlJitterSeconds());
        long jitter = jitterBound == 0
                ? 0
                : ThreadLocalRandom.current().nextLong(-jitterBound, jitterBound + 1);
        return Math.max(30, properties.getTtlSeconds() + jitter);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
