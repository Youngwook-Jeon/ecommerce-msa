package com.project.young.productservice.messaging.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

/**
 * Debezium {@code ExtractNewRecordState} JSON from {@code product_catalog_outbox}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProductCatalogChangedMessage(
        UUID id,
        UUID eventId,
        UUID productId,
        Long categoryId,
        String changeType,
        Instant occurredAt,
        Instant publishedAt,
        Instant createdAt
) {
}
