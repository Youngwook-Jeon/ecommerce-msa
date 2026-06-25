package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.dataaccess.cache.GuestCartDocumentJsonMapper;
import com.project.young.orderservice.dataaccess.config.GuestCartCacheProperties;
import com.project.young.orderservice.dataaccess.mapper.GuestCartDocumentMapper;
import com.project.young.orderservice.domain.entity.Cart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.CART_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisGuestCartRepositoryAdapterTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisGuestCartRepositoryAdapter repository;

    @BeforeEach
    void setUp() {
        GuestCartCacheProperties properties = new GuestCartCacheProperties();
        properties.setEnabled(true);
        properties.setKeyPrefix("ecomart:order:cart:guest:");
        properties.setTtlSeconds(2_592_000L);

        repository = new RedisGuestCartRepositoryAdapter(
                stringRedisTemplate,
                properties,
                new GuestCartDocumentMapper(),
                new GuestCartDocumentJsonMapper()
        );
    }

    @Test
    @DisplayName("insert: JSON과 TTL로 Redis에 저장한다")
    void insert_writesJsonWithTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        Cart cart = Cart.createForGuest(CART_ID);

        repository.insert(cart);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq("ecomart:order:cart:guest:" + CART_ID.getValue()),
                jsonCaptor.capture(),
                eq(Duration.ofSeconds(2_592_000L))
        );
        assertThat(jsonCaptor.getValue()).contains(CART_ID.getValue().toString());
    }

    @Test
    @DisplayName("findById: Redis JSON을 게스트 카트로 역직렬화한다")
    void findById_readsJson() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        String json = """
                {
                  "id":"%s",
                  "createdAt":"2026-06-01T00:00:00Z",
                  "updatedAt":"2026-06-01T00:00:00Z",
                  "items":[]
                }
                """.formatted(CART_ID.getValue());
        when(valueOperations.get("ecomart:order:cart:guest:" + CART_ID.getValue())).thenReturn(json);

        Cart cart = repository.findById(CART_ID).orElseThrow();

        assertThat(cart.getId()).isEqualTo(CART_ID);
        assertThat(cart.isGuestCart()).isTrue();
        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("delete: Redis 키를 삭제한다")
    void delete_removesKey() {
        repository.delete(CART_ID);

        verify(stringRedisTemplate).delete("ecomart:order:cart:guest:" + CART_ID.getValue());
    }
}
