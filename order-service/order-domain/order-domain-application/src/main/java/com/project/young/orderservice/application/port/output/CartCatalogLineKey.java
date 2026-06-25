package com.project.young.orderservice.application.port.output;

import java.util.UUID;

/**
 * Identifies a cart line for catalog resolution.
 * Order-service sends variant ids in {@code POST /public/catalog/cart-lines/search};
 * {@code productId} is used to validate the resolved catalog line matches the cart snapshot.
 */
public record CartCatalogLineKey(UUID productId, UUID productVariantId) {

    public CartCatalogLineKey {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        if (productVariantId == null) {
            throw new IllegalArgumentException("productVariantId must not be null");
        }
    }
}
