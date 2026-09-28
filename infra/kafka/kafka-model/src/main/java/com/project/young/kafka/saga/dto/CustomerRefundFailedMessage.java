package com.project.young.kafka.saga.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CustomerRefundFailedMessage(
        UUID id, UUID eventId, UUID refundId, UUID paymentId, UUID orderId,
        String userId, String failureReason, Instant occurredAt, Instant createdAt,
        long resultVersion, boolean failedAfterCompletion, Instant refundCompletedAt, Instant refundFailedAt
) {
    public CustomerRefundFailedMessage(UUID id, UUID eventId, UUID refundId, UUID paymentId, UUID orderId,
                                       String userId, String failureReason, Instant occurredAt, Instant createdAt) {
        this(id, eventId, refundId, paymentId, orderId, userId, failureReason, occurredAt, createdAt,
                2, false, null, occurredAt);
    }
}
