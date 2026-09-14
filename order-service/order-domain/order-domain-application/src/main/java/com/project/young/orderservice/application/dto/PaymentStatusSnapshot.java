package com.project.young.orderservice.application.dto;

import com.project.young.common.application.contract.payment.PaymentReconciliationStatus;

import java.time.Instant;
import java.util.UUID;

/** Safe Payment-service status projection consumed by Order reconciliation. */
public record PaymentStatusSnapshot(
        UUID paymentId,
        UUID orderId,
        PaymentReconciliationStatus status,
        Instant updatedAt
) {
}
