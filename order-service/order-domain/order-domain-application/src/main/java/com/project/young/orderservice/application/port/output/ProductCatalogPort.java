package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.application.port.output.view.CartCatalogLineView;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves storefront catalog data for cart mutations and {@code POST /carts/current/sync}.
 * Implemented via product-service REST in a later PR.
 */
public interface ProductCatalogPort {

    Optional<CartCatalogLineView> resolveLine(UUID productId, UUID productVariantId);
}
