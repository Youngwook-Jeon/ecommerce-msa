package com.project.young.orderservice.application.dto;

import java.time.Instant;
import java.util.UUID;

/** Minimal order projection for payment-status reconciliation. */
public record PendingPaymentOrderView(
        UUID orderId,
        String userId,
        Instant updatedAt
) {
}
