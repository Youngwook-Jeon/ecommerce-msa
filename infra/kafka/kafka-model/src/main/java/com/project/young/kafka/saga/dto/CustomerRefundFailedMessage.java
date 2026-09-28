package com.project.young.kafka.saga.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CustomerRefundFailedMessage(
        UUID id, UUID eventId, UUID refundId, UUID paymentId, UUID orderId,
        String userId, String failureReason, Instant occurredAt, Instant createdAt
) {
}
