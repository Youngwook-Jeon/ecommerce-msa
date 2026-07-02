package com.project.young.orderservice.web.cart.dto;

import com.project.young.orderservice.domain.merge.CartMergeSkipReason;
import lombok.Builder;

import java.util.UUID;

@Builder
public record CartMergeSkippedLineResponse(
        UUID productId,
        UUID productVariantId,
        String productName,
        int quantity,
        CartMergeSkipReason reason
) {
}
