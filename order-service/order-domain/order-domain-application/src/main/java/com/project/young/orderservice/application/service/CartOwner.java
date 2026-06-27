package com.project.young.orderservice.application.service;

import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.UserId;

/**
 * Identifies who owns the cart for application-service operations.
 * Controllers resolve JWT or guest cookie into a {@link CartOwner} once per request.
 */
public sealed interface CartOwner permits CartOwner.User, CartOwner.Guest {

    record User(UserId userId) implements CartOwner {
    }

    record Guest(CartId cartId) implements CartOwner {
    }

    static CartOwner forUser(UserId userId) {
        return new User(userId);
    }

    static CartOwner forGuest(CartId cartId) {
        return new Guest(cartId);
    }
}
