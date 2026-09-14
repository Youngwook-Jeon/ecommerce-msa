package com.project.young.paymentservice.application.dto.query;

import com.project.young.common.application.contract.payment.PaymentReconciliationStatus;

import java.time.Instant;
import java.util.UUID;

/** Minimal payment projection used by order-saga reconciliation. */
public record OrderPaymentStatusView(
        UUID paymentId,
        UUID orderId,
        PaymentReconciliationStatus status,
        Instant updatedAt
) {
}
