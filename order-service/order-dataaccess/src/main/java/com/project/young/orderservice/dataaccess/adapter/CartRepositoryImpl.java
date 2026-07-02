package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.dataaccess.entity.CartEntity;
import com.project.young.orderservice.dataaccess.mapper.CartAggregateMapper;
import com.project.young.orderservice.dataaccess.mapper.CartDataAccessMapper;
import com.project.young.orderservice.dataaccess.repository.CartJpaRepository;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.CartItem;
import com.project.young.orderservice.domain.exception.CartNotFoundException;
import com.project.young.orderservice.domain.repository.CartRepository;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.CartOwnerType;
import com.project.young.orderservice.domain.valueobject.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class CartRepositoryImpl implements CartRepository {

    private final CartJpaRepository cartJpaRepository;
    private final CartDataAccessMapper cartDataAccessMapper;
    private final CartAggregateMapper cartAggregateMapper;
    private final EntityManager entityManager;

    public CartRepositoryImpl(
            CartJpaRepository cartJpaRepository,
            CartDataAccessMapper cartDataAccessMapper,
            CartAggregateMapper cartAggregateMapper,
            EntityManager entityManager
    ) {
        this.cartJpaRepository = cartJpaRepository;
        this.cartDataAccessMapper = cartDataAccessMapper;
        this.cartAggregateMapper = cartAggregateMapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void insert(Cart cart) {
        if (cart == null) {
            throw new IllegalArgumentException("cart must not be null");
        }
        validateUserCart(cart);
        if (cart.getId() == null) {
            throw new IllegalArgumentException("cart id must not be null for insert");
        }
        for (CartItem item : cart.getItems()) {
            if (item.getId() == null) {
                throw new IllegalArgumentException("cart item id must not be null for insert");
            }
        }

        CartEntity toPersist = cartDataAccessMapper.cartToCartEntity(cart);
        entityManager.persist(toPersist);
    }

    @Override
    @Transactional
    public void update(Cart cart) {
        if (cart == null) {
            throw new IllegalArgumentException("cart must not be null");
        }
        validateUserCart(cart);
        if (cart.getId() == null) {
            throw new IllegalArgumentException("cart id must not be null for update");
        }
        for (CartItem item : cart.getItems()) {
            if (item.getId() == null) {
                throw new IllegalArgumentException("cart item id must not be null for update");
            }
        }

        CartEntity current = cartJpaRepository.findWithItemsById(cart.getId().getValue())
                .orElseThrow(() -> new CartNotFoundException("Cart not found: " + cart.getId().getValue()));

        cartDataAccessMapper.updateEntityFromDomain(cart, current);
        // Force-increment the aggregate-root version even when only cart_items changed,
        // so concurrent writers to the same cart conflict. Flush now to surface any
        // OptimisticLockException at this call (before the caller mutates external stores
        // such as the guest cart in Redis during a merge).
        entityManager.lock(current, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        entityManager.flush();
    }

    @Override
    public Optional<Cart> findByUserId(UserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return cartJpaRepository.findByUserId(userId.value()).map(cartAggregateMapper::toCart);
    }

    @Override
    public Optional<Cart> findById(CartId cartId) {
        if (cartId == null) {
            throw new IllegalArgumentException("cartId must not be null");
        }
        return cartJpaRepository.findWithItemsById(cartId.getValue()).map(cartAggregateMapper::toCart);
    }

    private static void validateUserCart(Cart cart) {
        if (cart != null && !cart.isUserCart()) {
            throw new IllegalArgumentException("CartRepository only supports user-owned carts");
        }
    }
}
