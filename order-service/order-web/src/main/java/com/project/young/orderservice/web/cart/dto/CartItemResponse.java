package com.project.young.orderservice.web.cart.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record CartItemResponse(
        UUID itemId,
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
    public CartItemResponse {
        variantOptions = variantOptions == null ? List.of() : List.copyOf(variantOptions);
    }
}
