package com.project.young.productservice.application.dto.event;

import java.util.UUID;

/**
 * Internal signal to evict storefront PDP cache after the mutating transaction commits.
 */
public record StorefrontProductDetailCacheEvictRequestedEvent(
        UUID productId,
        ProductCatalogChangeType changeType
) {
    public StorefrontProductDetailCacheEvictRequestedEvent {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        if (changeType == null) {
            throw new IllegalArgumentException("changeType must not be null");
        }
    }
}
