package com.project.young.orderservice.web.cart.dto;

import com.project.young.orderservice.domain.sync.CartSyncRemovalReason;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record CartSyncChangeResponse(
        CartSyncChangeType type,
        UUID itemId,
        BigDecimal previousPrice,
        BigDecimal currentPrice,
        Integer previousQuantity,
        Integer currentQuantity,
        String productName,
        CartSyncRemovalReason removalReason
) {
}
