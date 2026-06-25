package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.CartItem;
import com.project.young.orderservice.domain.exception.CartNotFoundException;
import com.project.young.orderservice.domain.repository.GuestCartRepository;
import com.project.young.orderservice.domain.valueobject.CartId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(prefix = "order-service.guest-cart", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryGuestCartRepositoryAdapter implements GuestCartRepository {

    private final Map<CartId, Cart> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<Cart> findById(CartId cartId) {
        validateCartId(cartId);
        return Optional.ofNullable(storage.get(cartId));
    }

    @Override
    public void insert(Cart cart) {
        validateGuestCartForWrite(cart);
        if (storage.containsKey(cart.getId())) {
            throw new IllegalStateException("Guest cart already exists: " + cart.getId().getValue());
        }
        storage.put(cart.getId(), cart);
    }

    @Override
    public void update(Cart cart) {
        validateGuestCartForWrite(cart);
        if (!storage.containsKey(cart.getId())) {
            throw new CartNotFoundException("Guest cart not found: " + cart.getId().getValue());
        }
        storage.put(cart.getId(), cart);
    }

    @Override
    public void delete(CartId cartId) {
        validateCartId(cartId);
        storage.remove(cartId);
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
