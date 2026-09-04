package com.project.young.orderservice.application.dto.event;

import java.time.Instant;
import java.util.UUID;

/** Durable request for Payment Service to refund a captured payment. */
public record RefundRequestedEvent(
        UUID compensationEventId,
        UUID paymentId,
        UUID orderId,
        String userId,
        String reason,
        Instant occurredAt
) {
}
