package com.project.young.orderservice.application.dto;

import com.project.young.orderservice.application.reconciliation.OrderPaymentReconciliationManualOperation;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit projection for a committed manual reconciliation operation. */
public record OrderPaymentReconciliationOperationAuditView(
        UUID id,
        UUID orderId,
        String operatorId,
        UUID requestId,
        OrderPaymentReconciliationManualOperation operation,
        String reason,
        UUID compensationEventId,
        Instant occurredAt
) {
}
