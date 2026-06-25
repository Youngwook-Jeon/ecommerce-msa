package com.project.young.orderservice.domain.repository;

import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.valueobject.CartId;

import java.util.Optional;

public interface GuestCartRepository {

    Optional<Cart> findById(CartId cartId);

    void insert(Cart cart);

    void update(Cart cart);

    void delete(CartId cartId);
}
