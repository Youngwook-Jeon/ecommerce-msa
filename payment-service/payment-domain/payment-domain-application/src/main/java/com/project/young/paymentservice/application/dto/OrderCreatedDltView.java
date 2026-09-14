package com.project.young.paymentservice.application.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderCreatedDltView(
        UUID eventId,
        UUID orderId,
        String userId,
        String totalAmount,
        String currency,
        Instant createdAt,
        int replayAttempts
) {
}
