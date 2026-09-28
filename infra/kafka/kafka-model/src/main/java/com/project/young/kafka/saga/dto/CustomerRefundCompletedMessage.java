package com.project.young.kafka.saga.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CustomerRefundCompletedMessage(
        UUID id, UUID eventId, UUID refundId, UUID paymentId, UUID orderId,
        String userId, Instant occurredAt, Instant createdAt, long resultVersion, Instant refundCompletedAt
) {
    public CustomerRefundCompletedMessage(UUID id, UUID eventId, UUID refundId, UUID paymentId, UUID orderId,
                                          String userId, Instant occurredAt, Instant createdAt) {
        this(id, eventId, refundId, paymentId, orderId, userId, occurredAt, createdAt, 1, occurredAt);
    }
}
