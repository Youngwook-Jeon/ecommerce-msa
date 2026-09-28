package com.project.young.orderservice.application.dto.command;

import java.time.Instant;
import java.util.Objects;

import java.util.UUID;

public record ApplyCustomerRefundResultCommand(
        UUID refundId, UUID paymentId, UUID orderId, String userId,
        boolean succeeded, String failureReason, long resultVersion, boolean failedAfterCompletion,
        Instant refundCompletedAt, Instant refundFailedAt
) {
    public ApplyCustomerRefundResultCommand(UUID refundId, UUID paymentId, UUID orderId, String userId,
                                            boolean succeeded, String failureReason) {
        this(refundId, paymentId, orderId, userId, succeeded, failureReason, succeeded ? 1 : 2,
                false, null, null);
    }

    public ApplyCustomerRefundResultCommand {
        Objects.requireNonNull(refundId, "refundId must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        if (userId == null || userId.isBlank() || resultVersion != (succeeded ? 1 : 2)
                || (succeeded && failedAfterCompletion)
                || (failedAfterCompletion && refundCompletedAt == null)) {
            throw new IllegalArgumentException("Invalid customer refund result metadata");
        }
    }
}
