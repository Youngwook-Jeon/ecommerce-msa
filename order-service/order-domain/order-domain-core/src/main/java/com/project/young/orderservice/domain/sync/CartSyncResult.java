package com.project.young.orderservice.domain.sync;

import com.project.young.orderservice.domain.entity.Cart;

import java.util.List;

public record CartSyncResult(Cart cart, List<CartSyncChange> changes) {

    public CartSyncResult {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
