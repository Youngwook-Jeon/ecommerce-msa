package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record AddProductVariantResponse(
        UUID productId,
        UUID productVariantId,
        String sku,
        int stockQuantity,
        String status,
        BigDecimal calculatedPrice,
        String message
) {
}
