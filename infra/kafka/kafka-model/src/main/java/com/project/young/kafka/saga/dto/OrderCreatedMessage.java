package com.project.young.kafka.saga.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

/**
 * Debezium {@code ExtractNewRecordState} JSON from {@code orders.order_outbox}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OrderCreatedMessage(
        UUID id,
        UUID eventId,
        UUID orderId,
        String userId,
        String totalAmount,
        String currency,
        Instant occurredAt,
        Instant publishedAt,
        Instant createdAt
) {
}
