package com.project.young.orderservice.domain.sync;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.orderservice.domain.valueobject.CartItemId;

public sealed interface CartSyncChange permits
        CartSyncChange.PriceUpdated,
        CartSyncChange.SnapshotUpdated,
        CartSyncChange.QuantityAdjusted,
        CartSyncChange.Removed {

    CartItemId itemId();

    record PriceUpdated(CartItemId itemId, Money previousPrice, Money currentPrice) implements CartSyncChange {
    }

    record SnapshotUpdated(CartItemId itemId) implements CartSyncChange {
    }

    record QuantityAdjusted(CartItemId itemId, int previousQuantity, int currentQuantity) implements CartSyncChange {
    }

    record Removed(CartItemId itemId, String productName, CartSyncRemovalReason reason) implements CartSyncChange {
    }
}
