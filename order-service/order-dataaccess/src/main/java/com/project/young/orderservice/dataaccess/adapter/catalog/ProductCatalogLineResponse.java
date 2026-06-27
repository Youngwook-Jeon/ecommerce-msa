package com.project.young.orderservice.dataaccess.adapter.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductCatalogLineResponse(
        UUID productId,
        UUID productVariantId,
        String productName,
        String brand,
        String sku,
        String imageUrl,
        BigDecimal unitPrice,
        boolean purchasable,
        int stockQuantity,
        List<ProductCatalogOptionLineResponse> variantOptions
) {

    public ProductCatalogLineResponse {
        variantOptions = variantOptions == null ? List.of() : List.copyOf(variantOptions);
    }
}
