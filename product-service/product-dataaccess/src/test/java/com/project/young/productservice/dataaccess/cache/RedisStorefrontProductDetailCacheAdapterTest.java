package com.project.young.productservice.dataaccess.cache;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.dataaccess.config.StorefrontProductCacheProperties;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisStorefrontProductDetailCacheAdapterTest {

    private static final String KEY_PREFIX = "ecomart:product:storefront:";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ReadProductDetailViewJsonMapper jsonMapper;

    private StorefrontProductCacheProperties properties;
    private RedisStorefrontProductDetailCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new StorefrontProductCacheProperties();
        properties.setKeyPrefix(KEY_PREFIX);
        properties.setTtlSeconds(900);
        properties.setTtlJitterSeconds(0);
        properties.setLockTtlSeconds(10);
        properties.setLockWaitRetries(0);
        properties.setLockWaitMillis(0);

        adapter = new RedisStorefrontProductDetailCacheAdapter(
                stringRedisTemplate,
                properties,
                jsonMapper
        );
    }

    private void stubValueOperations() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("findCached: 캐시 miss이면 empty를 반환한다")
    void findCached_whenMissing_returnsEmpty() {
        stubValueOperations();
        ProductId productId = new ProductId(UUID.randomUUID());
        when(valueOperations.get(cacheKey(productId))).thenReturn(null);

        assertThat(adapter.findCached(productId)).isEmpty();
    }

    @Test
    @DisplayName("findCached: blank JSON이면 empty를 반환한다")
    void findCached_whenBlankJson_returnsEmpty() {
        stubValueOperations();
        ProductId productId = new ProductId(UUID.randomUUID());
        when(valueOperations.get(cacheKey(productId))).thenReturn("   ");

        assertThat(adapter.findCached(productId)).isEmpty();
        verify(jsonMapper, never()).fromJson(any());
    }

    @Test
    @DisplayName("findCached: 캐시 hit이면 역직렬화한 view를 반환한다")
    void findCached_whenHit_returnsView() {
        stubValueOperations();
        ProductId productId = new ProductId(UUID.randomUUID());
        ReadProductDetailView view = sampleView(productId.getValue());
        String json = "{\"id\":\"" + productId.getValue() + "\"}";

        when(valueOperations.get(cacheKey(productId))).thenReturn(json);
        when(jsonMapper.fromJson(json)).thenReturn(view);

        assertThat(adapter.findCached(productId)).contains(view);
    }

    @Test
    @DisplayName("put: JSON과 TTL로 Redis에 저장한다")
    void put_storesJsonWithTtl() {
        stubValueOperations();
        ProductId productId = new ProductId(UUID.randomUUID());
        ReadProductDetailView view = sampleView(productId.getValue());
        String json = "{\"name\":\"Preview Product\"}";

        when(jsonMapper.toJson(view)).thenReturn(json);

        adapter.put(productId, view);

        verify(valueOperations).set(
                eq(cacheKey(productId)),
                eq(json),
                eq(Duration.ofSeconds(900))
        );
    }

    @Test
    @DisplayName("evict: 캐시 키를 삭제한다")
    void evict_deletesCacheKey() {
        ProductId productId = new ProductId(UUID.randomUUID());

        adapter.evict(productId);

        verify(stringRedisTemplate).delete(cacheKey(productId));
    }

    @Test
    @DisplayName("getOrLoad: 캐시 hit이면 loader를 호출하지 않는다")
    void getOrLoad_whenCacheHit_doesNotCallLoader() {
        stubValueOperations();
        ProductId productId = new ProductId(UUID.randomUUID());
        ReadProductDetailView cached = sampleView(productId.getValue());
        String json = "{\"cached\":true}";

        when(valueOperations.get(cacheKey(productId))).thenReturn(json);
        when(jsonMapper.fromJson(json)).thenReturn(cached);

        Supplier<Optional<ReadProductDetailView>> loader = () -> {
            throw new AssertionError("loader must not be called on cache hit");
        };

        assertThat(adapter.getOrLoad(productId, loader)).contains(cached);
    }

    @Test
    @DisplayName("getOrLoad: miss 후 lock 획득 시 loader 결과를 캐시에 저장한다")
    void getOrLoad_whenLockAcquired_loadsAndCaches() {
        stubValueOperations();
        ProductId productId = new ProductId(UUID.randomUUID());
        ReadProductDetailView loaded = sampleView(productId.getValue());
        String json = "{\"loaded\":true}";

        when(valueOperations.get(cacheKey(productId))).thenReturn(null);
        when(valueOperations.setIfAbsent(eq(lockKey(productId)), any(), eq(Duration.ofSeconds(10))))
                .thenReturn(true);
        when(jsonMapper.toJson(loaded)).thenReturn(json);

        Optional<ReadProductDetailView> result = adapter.getOrLoad(productId, () -> Optional.of(loaded));

        assertThat(result).contains(loaded);
        verify(valueOperations).set(cacheKey(productId), json, Duration.ofSeconds(900));
        verify(stringRedisTemplate).execute(any(DefaultRedisScript.class), eq(List.of(lockKey(productId))), any());
    }

    @Test
    @DisplayName("getOrLoad: lock 미획득 시 loader를 직접 호출한다")
    void getOrLoad_whenLockNotAcquired_loadsWithoutBlocking() {
        stubValueOperations();
        ProductId productId = new ProductId(UUID.randomUUID());
        ReadProductDetailView loaded = sampleView(productId.getValue());
        String json = "{\"loaded\":true}";

        when(valueOperations.get(cacheKey(productId))).thenReturn(null);
        when(valueOperations.setIfAbsent(eq(lockKey(productId)), any(), eq(Duration.ofSeconds(10))))
                .thenReturn(false);
        when(jsonMapper.toJson(loaded)).thenReturn(json);

        Optional<ReadProductDetailView> result = adapter.getOrLoad(productId, () -> Optional.of(loaded));

        assertThat(result).contains(loaded);
        verify(valueOperations).set(cacheKey(productId), json, Duration.ofSeconds(900));
        verify(stringRedisTemplate, never()).execute(any(DefaultRedisScript.class), any(), any());
    }

    @Test
    @DisplayName("getOrLoad: loader가 empty면 캐시에 저장하지 않는다")
    void getOrLoad_whenLoaderEmpty_doesNotPut() {
        stubValueOperations();
        ProductId productId = new ProductId(UUID.randomUUID());

        when(valueOperations.get(cacheKey(productId))).thenReturn(null);
        when(valueOperations.setIfAbsent(eq(lockKey(productId)), any(), eq(Duration.ofSeconds(10))))
                .thenReturn(true);

        assertThat(adapter.getOrLoad(productId, Optional::empty)).isEmpty();

        verify(valueOperations, never()).set(eq(cacheKey(productId)), any(), any(Duration.class));
    }

    private static ReadProductDetailView sampleView(UUID productId) {
        return ReadProductDetailView.builder()
                .id(productId)
                .categoryId(4L)
                .name("Preview Product")
                .description("desc")
                .brand("Brand")
                .mainImageUrl("https://example.com/a.jpg")
                .basePrice(new BigDecimal("10000"))
                .status(ProductStatus.INACTIVE)
                .conditionType(ConditionType.NEW)
                .optionGroups(List.of())
                .variants(List.of())
                .build();
    }

    private static String cacheKey(ProductId productId) {
        return KEY_PREFIX + "detail:" + productId.getValue();
    }

    private static String lockKey(ProductId productId) {
        return KEY_PREFIX + "lock:" + productId.getValue();
    }
}
