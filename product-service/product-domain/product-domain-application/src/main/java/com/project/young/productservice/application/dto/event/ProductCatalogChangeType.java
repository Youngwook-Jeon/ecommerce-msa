package com.project.young.productservice.application.dto.event;

/**
 * Storefront catalog mutation kinds for cache invalidation events.
 */
public enum ProductCatalogChangeType {
    PRODUCT_UPDATED,
    STATUS_CHANGED,
    VARIANT_CHANGED,
    OPTION_CHANGED,
    IMAGE_CHANGED,
    DELETED
}
