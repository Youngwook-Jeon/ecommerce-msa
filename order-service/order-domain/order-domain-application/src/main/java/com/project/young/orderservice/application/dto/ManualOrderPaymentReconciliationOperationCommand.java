package com.project.young.orderservice.application.dto;

import java.util.UUID;

/** Request context captured for an operator-initiated reconciliation action. */
public record ManualOrderPaymentReconciliationOperationCommand(
        UUID orderId,
        String operatorId,
        UUID requestId,
        String reason
) {
}
