package com.project.young.orderservice.domain.repository;

import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.UserId;

import java.util.Optional;

public interface CartRepository {

    void insert(Cart cart);

    void update(Cart cart);

    Optional<Cart> findByUserId(UserId userId);

    Optional<Cart> findById(CartId cartId);
}
