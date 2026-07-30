package com.project.young.kafka.saga.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

/**
 * Debezium {@code ExtractNewRecordState} JSON from {@code payments.payment_outbox}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentFailedMessage(
        UUID id,
        UUID eventId,
        UUID paymentId,
        UUID orderId,
        String userId,
        String amount,
        String failureReason,
        Instant occurredAt,
        Instant publishedAt,
        Instant createdAt
) {
}
