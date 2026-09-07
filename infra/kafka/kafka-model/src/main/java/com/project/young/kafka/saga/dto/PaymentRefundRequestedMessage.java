package com.project.young.kafka.saga.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

/**
 * Debezium {@code ExtractNewRecordState} JSON from {@code orders.refund_requested_outbox}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentRefundRequestedMessage(
        UUID id,
        UUID compensationEventId,
        UUID paymentId,
        UUID orderId,
        String userId,
        String reason,
        Instant occurredAt,
        Instant createdAt
) {
}
