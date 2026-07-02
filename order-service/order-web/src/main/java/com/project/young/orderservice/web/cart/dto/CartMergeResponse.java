package com.project.young.orderservice.web.cart.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record CartMergeResponse(
        CartResponse cart,
        int mergedLineCount,
        List<CartMergeSkippedLineResponse> skippedLines,
        List<CartSyncChangeResponse> syncChanges
) {
    public CartMergeResponse {
        skippedLines = skippedLines == null ? List.of() : List.copyOf(skippedLines);
        syncChanges = syncChanges == null ? List.of() : List.copyOf(syncChanges);
    }
}
