package com.project.young.productservice.application.dto;

import com.project.young.productservice.domain.valueobject.ProductStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record UpdateProductVariantResult(
        UUID productId,
        UUID productVariantId,
        String sku,
        int stockQuantity,
        ProductStatus status,
        BigDecimal calculatedPrice
) {
}
