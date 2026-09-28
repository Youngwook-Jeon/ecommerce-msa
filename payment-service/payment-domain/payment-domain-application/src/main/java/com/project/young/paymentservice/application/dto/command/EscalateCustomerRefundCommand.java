package com.project.young.paymentservice.application.dto.command;

import java.util.Objects;
import java.util.UUID;

/** An unknown/rejected outcome needs operator review, not a business FAILED result. */
public record EscalateCustomerRefundCommand(UUID refundId, UUID paymentId, String providerRefundId,
                                            String failureExceptionClass, String failureMessage) {
    public EscalateCustomerRefundCommand {
        Objects.requireNonNull(refundId, "refundId must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        if (failureExceptionClass == null || failureExceptionClass.isBlank()) {
            throw new IllegalArgumentException("failureExceptionClass must not be blank");
        }
    }
}
