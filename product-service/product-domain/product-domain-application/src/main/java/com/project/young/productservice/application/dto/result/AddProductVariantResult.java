package com.project.young.productservice.application.dto.result;

import com.project.young.productservice.domain.valueobject.ProductStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record AddProductVariantResult(
        UUID productId,
        UUID productVariantId,
        String sku,
        int stockQuantity,
        ProductStatus status,
        BigDecimal calculatedPrice
) {
}
