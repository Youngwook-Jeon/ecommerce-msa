package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record DeleteProductVariantResponse(
        UUID productId,
        UUID productVariantId,
        String sku,
        String status,
        String message
) {
}
