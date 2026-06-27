package com.project.young.orderservice.web.cart.dto;

import com.project.young.orderservice.domain.valueobject.CartOwnerType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record CartResponse(
        UUID cartId,
        CartOwnerType ownerType,
        String userId,
        List<CartItemResponse> items,
        int itemCount,
        int totalQuantity,
        BigDecimal subtotal,
        Instant createdAt,
        Instant updatedAt
) {
    public CartResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
