package com.project.young.orderservice.application.dto.event;

import com.project.young.orderservice.domain.entity.CustomerRefund;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Customer-requested refund, intentionally distinct from saga compensation refunds.
 */
public record CustomerRefundRequestedEvent(
        UUID refundId,
        UUID paymentId,
        UUID orderId,
        String userId,
        String reason,
        Instant occurredAt
) {

    public CustomerRefundRequestedEvent {
        Objects.requireNonNull(refundId, "refundId must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");

        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (reason.isBlank() || reason.length() > CustomerRefund.MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("reason must be between 1 and 512 characters");
        }
    }
}
