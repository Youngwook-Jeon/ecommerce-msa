package com.project.young.productservice.application.dto.event;

import java.time.Instant;
import java.util.UUID;

public record ProductCatalogChangedEvent(
        UUID eventId,
        UUID productId,
        Long categoryId,
        ProductCatalogChangeType changeType,
        Instant occurredAt
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
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
