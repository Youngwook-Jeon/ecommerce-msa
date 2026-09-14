package com.project.young.paymentservice.application.dto.query;

import com.project.young.paymentservice.application.compensation.OrderCreatedDltStatus;

import java.time.Instant;
import java.util.UUID;

/** Safe operational projection for an order.created dead-letter record. */
public record OrderCreatedDltOperationsView(
        UUID eventId,
        UUID orderId,
        String userId,
        String totalAmount,
        String currency,
        OrderCreatedDltStatus status,
        int replayAttempts,
        String failureExceptionClass,
        String failureMessage,
        Instant createdAt,
        Instant replayStartedAt
) {
}
