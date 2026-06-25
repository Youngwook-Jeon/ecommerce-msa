package com.project.young.productservice.application.port.output.view;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record ReadCartCatalogLineView(
        UUID productId,
        UUID productVariantId,
        String productName,
        String brand,
        String sku,
        String imageUrl,
        BigDecimal unitPrice,
        boolean purchasable,
        int stockQuantity,
        List<ReadCartCatalogOptionLineView> variantOptions
) {
    public ReadCartCatalogLineView {
        variantOptions = variantOptions == null ? List.of() : List.copyOf(variantOptions);
    }
}
