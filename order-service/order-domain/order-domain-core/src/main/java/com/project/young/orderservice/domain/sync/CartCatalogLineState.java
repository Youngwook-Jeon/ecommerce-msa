package com.project.young.orderservice.domain.sync;

import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;

import java.util.Objects;

/**
 * Catalog validation outcome for a single cart line, produced by {@code ProductCatalogPort}
 * and consumed by {@link Cart#reconcileWithCatalog}.
 */
public record CartCatalogLineState(
        boolean available,
        CartSyncRemovalReason removalReason,
        int stockQuantity,
        CartItemSnapshot snapshot
) {

    public CartCatalogLineState {
        if (!available) {
            Objects.requireNonNull(removalReason, "removalReason is required when line is unavailable");
            snapshot = null;
            stockQuantity = 0;
        } else {
            Objects.requireNonNull(snapshot, "snapshot is required when line is available");
            removalReason = null;
            if (stockQuantity < 0) {
                throw new IllegalArgumentException("stockQuantity must not be negative");
            }
        }
    }

    public static CartCatalogLineState available(CartItemSnapshot snapshot, int stockQuantity) {
        return new CartCatalogLineState(true, null, stockQuantity, snapshot);
    }

    public static CartCatalogLineState unavailable(CartSyncRemovalReason reason) {
        return new CartCatalogLineState(false, reason, 0, null);
    }
}
