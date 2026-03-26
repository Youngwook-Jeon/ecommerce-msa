package com.project.young.productservice.application.dto.result;

import com.project.young.productservice.domain.valueobject.ProductStatus;
import lombok.Builder;

import java.util.UUID;

@Builder
public record DeleteProductVariantResult(
        UUID productId,
        UUID productVariantId,
        String sku,
        ProductStatus status
) {
}
