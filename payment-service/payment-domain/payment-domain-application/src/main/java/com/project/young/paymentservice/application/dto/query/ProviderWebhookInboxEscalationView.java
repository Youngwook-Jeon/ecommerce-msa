package com.project.young.paymentservice.application.dto.query;

import java.time.Instant;

/** Safe operational projection; it intentionally excludes the raw signed webhook payload. */
public record ProviderWebhookInboxEscalationView(
        String eventId,
        String provider,
        String providerPaymentId,
        String outcome,
        int attempts,
        String failureMessage,
        Instant receivedAt,
        Instant updatedAt
) {
}
