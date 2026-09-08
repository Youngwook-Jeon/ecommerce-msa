package com.project.young.kafka.saga.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

/** Debezium JSON from Order Service inventory release compensation outbox. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InventoryReleaseRequestedMessage(
        UUID id, UUID compensationEventId, UUID orderId, String reason, Instant occurredAt, Instant createdAt
) {
}
