package com.project.young.productservice.application.dto.event;

import java.time.Instant;
import java.util.UUID;

public record ProductCatalogChangedEvent(
        UUID eventId,
        UUID productId,
        Long categoryId,
        ProductCatalogChangeType changeType,
        Instant occurredAt,
        long expectedVersion
) {
    public ProductCatalogChangedEvent {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        if (changeType == null) {
            throw new IllegalArgumentException("changeType must not be null");
        }
        if (changeType == ProductCatalogChangeType.PRODUCT_CREATED ? expectedVersion != 0 : expectedVersion < 1) {
            throw new IllegalArgumentException("Expected version must be zero for creation and positive for changes");
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
