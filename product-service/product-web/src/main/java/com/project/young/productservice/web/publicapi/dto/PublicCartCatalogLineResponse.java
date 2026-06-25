package com.project.young.productservice.web.publicapi.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record PublicCartCatalogLineResponse(
        UUID productId,
        UUID productVariantId,
        String productName,
        String brand,
        String sku,
        String imageUrl,
        BigDecimal unitPrice,
        boolean purchasable,
        int stockQuantity,
        List<PublicCartCatalogOptionLineResponse> variantOptions
) {
    public PublicCartCatalogLineResponse {
        variantOptions = variantOptions == null ? List.of() : List.copyOf(variantOptions);
    }
}
