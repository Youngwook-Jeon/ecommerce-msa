package com.project.young.orderservice.web.order.dto;

import com.project.young.orderservice.web.cart.dto.CartItemOptionResponse;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderLineResponse(
        UUID lineId,
        UUID productId,
        UUID productVariantId,
        String productName,
        String brand,
        String sku,
        String imageUrl,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineAmount,
        List<CartItemOptionResponse> variantOptions
) {
    public OrderLineResponse {
        variantOptions = variantOptions == null ? List.of() : List.copyOf(variantOptions);
    }
}
