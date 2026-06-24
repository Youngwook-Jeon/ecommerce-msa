package com.project.young.orderservice.application.port.output.view;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Read model returned by {@link com.project.young.orderservice.application.port.output.ProductCatalogPort}.
 * Mapped to {@link com.project.young.orderservice.domain.valueobject.CartItemSnapshot} in application layer.
 */
public record CartCatalogLineView(
        UUID productId,
        UUID productVariantId,
        String productName,
        String brand,
        String sku,
        String imageUrl,
        BigDecimal unitPrice,
        boolean purchasable,
        int stockQuantity,
        List<CartCatalogOptionLineView> variantOptions
) {

    public CartCatalogLineView {
        variantOptions = variantOptions == null ? List.of() : List.copyOf(variantOptions);
    }
}
