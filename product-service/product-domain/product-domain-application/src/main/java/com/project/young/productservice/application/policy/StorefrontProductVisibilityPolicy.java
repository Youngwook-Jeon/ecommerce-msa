package com.project.young.productservice.application.policy;

import com.project.young.productservice.domain.valueobject.ProductStatus;

/**
 * Storefront visibility rules for catalog list vs product detail (PDP).
 * <p>
 * PLP/search list only surfaces {@link ProductStatus#ACTIVE} products (ACTIVE categories only in Phase 1 queries). PDP allows direct access for published-but-not-listed statuses (e.g. {@link ProductStatus#INACTIVE})
 * for preview and pre-launch, while {@link ProductStatus#DRAFT} and {@link ProductStatus#DELETED} return 404.
 */
public final class StorefrontProductVisibilityPolicy {

    private StorefrontProductVisibilityPolicy() {
    }

    /**
     * Whether a product may appear on category PLP, facets, and search listing.
     */
    public static boolean isListedInCatalog(ProductStatus status) {
        return status != null && status.isActive();
    }

    /**
     * Whether {@code GET /public/products/{id}} returns 200 (not 404).
     * Includes {@link ProductStatus#INACTIVE}, {@link ProductStatus#OUT_OF_STOCK}, and
     * {@link ProductStatus#DISCONTINUED} for display-only PDP; excludes draft and deleted.
     */
    public static boolean isDetailViewable(ProductStatus status) {
        if (status == null) {
            return false;
        }
        return !status.isDraft() && !status.isDeleted();
    }

    /**
     * Whether the storefront may offer add-to-cart / checkout for this product (Phase 3+).
     */
    public static boolean isPurchasable(ProductStatus status) {
        return status != null && status.isActive();
    }
}
