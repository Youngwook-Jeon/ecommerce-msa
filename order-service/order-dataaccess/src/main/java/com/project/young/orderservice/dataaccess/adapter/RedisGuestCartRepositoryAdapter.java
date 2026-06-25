package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.dataaccess.cache.GuestCartDocument;
import com.project.young.orderservice.dataaccess.cache.GuestCartDocumentJsonMapper;
import com.project.young.orderservice.dataaccess.config.GuestCartCacheProperties;
import com.project.young.orderservice.dataaccess.mapper.GuestCartDocumentMapper;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.CartItem;
import com.project.young.orderservice.domain.exception.CartNotFoundException;
import com.project.young.orderservice.domain.repository.GuestCartRepository;
import com.project.young.orderservice.domain.valueobject.CartId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "order-service.guest-cart", name = "enabled", havingValue = "true")
public class RedisGuestCartRepositoryAdapter implements GuestCartRepository {

    private final StringRedisTemplate stringRedisTemplate;
    private final GuestCartCacheProperties properties;
    private final GuestCartDocumentMapper documentMapper;
    private final GuestCartDocumentJsonMapper jsonMapper;

    public RedisGuestCartRepositoryAdapter(
            StringRedisTemplate stringRedisTemplate,
            GuestCartCacheProperties properties,
            GuestCartDocumentMapper documentMapper,
            GuestCartDocumentJsonMapper jsonMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.documentMapper = documentMapper;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<Cart> findById(CartId cartId) {
        validateCartId(cartId);
        String json = stringRedisTemplate.opsForValue().get(cacheKey(cartId));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        GuestCartDocument document = jsonMapper.fromJson(json);
        return Optional.of(documentMapper.toCart(document));
    }

    @Override
    public void insert(Cart cart) {
        validateGuestCartForWrite(cart);
        Instant now = Instant.now();
        GuestCartDocument document = documentMapper.toDocument(cart, now);
        saveDocument(document);
    }

    @Override
    public void update(Cart cart) {
        validateGuestCartForWrite(cart);
        if (!exists(cart.getId())) {
            throw new CartNotFoundException("Guest cart not found: " + cart.getId().getValue());
        }
        Instant now = Instant.now();
        GuestCartDocument document = documentMapper.toDocument(cart, now);
        saveDocument(document);
    }

    @Override
    public void delete(CartId cartId) {
        validateCartId(cartId);
        stringRedisTemplate.delete(cacheKey(cartId));
    }

    private void saveDocument(GuestCartDocument document) {
        stringRedisTemplate.opsForValue().set(
                cacheKey(new CartId(document.getId())),
                jsonMapper.toJson(document),
                Duration.ofSeconds(properties.getTtlSeconds())
        );
    }

    private boolean exists(CartId cartId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(cacheKey(cartId)));
    }

    private String cacheKey(CartId cartId) {
        return properties.getKeyPrefix() + cartId.getValue();
    }

    private static void validateCartId(CartId cartId) {
        if (cartId == null) {
            throw new IllegalArgumentException("cartId must not be null");
        }
    }

    private static void validateGuestCartForWrite(Cart cart) {
        if (cart == null) {
            throw new IllegalArgumentException("cart must not be null");
        }
        if (!cart.isGuestCart()) {
            throw new IllegalArgumentException("GuestCartRepository only supports guest-owned carts");
        }
        if (cart.getId() == null) {
            throw new IllegalArgumentException("cart id must not be null");
        }
        for (CartItem item : cart.getItems()) {
            if (item.getId() == null) {
                throw new IllegalArgumentException("cart item id must not be null");
            }
        }
    }
}
