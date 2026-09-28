package com.project.young.paymentservice.application.dto.event;

import java.time.Instant;
import java.util.UUID;

public record CustomerRefundCompletedEvent(
        UUID eventId, UUID refundId, UUID paymentId, UUID orderId, String userId, Instant occurredAt,
        long resultVersion, Instant refundCompletedAt
) {
    public CustomerRefundCompletedEvent(UUID eventId, UUID refundId, UUID paymentId, UUID orderId,
                                        String userId, Instant occurredAt) {
        this(eventId, refundId, paymentId, orderId, userId, occurredAt, 1, occurredAt);
    }
}
