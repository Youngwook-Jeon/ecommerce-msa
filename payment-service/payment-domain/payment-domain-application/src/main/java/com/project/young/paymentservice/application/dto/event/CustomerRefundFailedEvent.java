package com.project.young.paymentservice.application.dto.event;

import java.time.Instant;
import java.util.UUID;

public record CustomerRefundFailedEvent(
        UUID eventId, UUID refundId, UUID paymentId, UUID orderId, String userId,
        String failureReason, Instant occurredAt
) {
}
