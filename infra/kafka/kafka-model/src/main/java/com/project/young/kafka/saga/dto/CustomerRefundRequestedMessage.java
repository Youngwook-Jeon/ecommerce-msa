package com.project.young.kafka.saga.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

/** Debezium payload relayed from {@code orders.customer_refund_requested_outbox}. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CustomerRefundRequestedMessage(
        UUID id,
        UUID refundId,
        UUID paymentId,
        UUID orderId,
        String userId,
        String reason,
        Instant occurredAt,
        Instant createdAt
) {
}
