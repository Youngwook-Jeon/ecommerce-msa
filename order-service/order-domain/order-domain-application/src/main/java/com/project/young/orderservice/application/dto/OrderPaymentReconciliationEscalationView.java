package com.project.young.orderservice.application.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderPaymentReconciliationEscalationView(
        UUID orderId,
        String userId,
        UUID paymentId,
        String paymentStatus,
        int attempts,
        String lastError,
        Instant firstFailureAt,
        Instant lastFailureAt
) {
}
