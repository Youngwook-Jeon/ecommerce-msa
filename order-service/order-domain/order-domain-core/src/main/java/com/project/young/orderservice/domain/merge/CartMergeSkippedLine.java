package com.project.young.orderservice.domain.merge;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.domain.entity.CartItem;

import java.util.Objects;

public record CartMergeSkippedLine(
        ProductId productId,
        ProductVariantId productVariantId,
        String productName,
        int quantity,
        CartMergeSkipReason reason
) {

    public CartMergeSkippedLine {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(productVariantId, "productVariantId must not be null");
        Objects.requireNonNull(productName, "productName must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    public static CartMergeSkippedLine fromGuestItem(CartItem guestItem, CartMergeSkipReason reason) {
        Objects.requireNonNull(guestItem, "guestItem must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        return new CartMergeSkippedLine(
                guestItem.getProductId(),
                guestItem.getProductVariantId(),
                guestItem.getSnapshot().productName(),
                guestItem.getQuantity(),
                reason
        );
    }
}
