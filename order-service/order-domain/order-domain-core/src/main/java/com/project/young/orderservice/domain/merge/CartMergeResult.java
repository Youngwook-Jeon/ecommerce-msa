package com.project.young.orderservice.domain.merge;

import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.sync.CartSyncChange;

import java.util.List;

public record CartMergeResult(
        Cart cart,
        int mergedLineCount,
        List<CartMergeSkippedLine> skippedLines,
        List<CartSyncChange> syncChanges
) {

    public CartMergeResult {
        skippedLines = skippedLines == null ? List.of() : List.copyOf(skippedLines);
        syncChanges = syncChanges == null ? List.of() : List.copyOf(syncChanges);
        if (mergedLineCount < 0) {
            throw new IllegalArgumentException("mergedLineCount must not be negative");
        }
    }

    public static CartMergeResult noGuestCart(Cart userCart) {
        return new CartMergeResult(userCart, 0, List.of(), List.of());
    }

    public static CartMergeResult emptyGuest(Cart userCart) {
        return new CartMergeResult(userCart, 0, List.of(), List.of());
    }
}
