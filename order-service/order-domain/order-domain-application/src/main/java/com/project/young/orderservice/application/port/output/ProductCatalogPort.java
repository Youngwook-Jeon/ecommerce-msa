package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.application.port.output.view.CartCatalogLineView;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves storefront catalog data for cart mutations and {@code POST /carts/current/sync}.
 * Implemented via product-service {@code POST /public/catalog/cart-lines/search}.
 * Implementations must resolve all requested lines in a single outbound call (batch).
 */
public interface ProductCatalogPort {

    /**
     * @param lines distinct cart lines to resolve; empty collection yields empty map
     * @return resolved lines keyed by {@code productVariantId}; absent keys mean variant not found
     */
    Map<UUID, CartCatalogLineView> resolveLines(Collection<CartCatalogLineKey> lines);
}
