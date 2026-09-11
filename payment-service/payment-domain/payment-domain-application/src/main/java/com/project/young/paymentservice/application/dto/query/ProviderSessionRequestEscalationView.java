package com.project.young.paymentservice.application.dto.query;

import java.time.Instant;
import java.util.UUID;

/** Safe operational projection; it intentionally excludes the provider client secret. */
public record ProviderSessionRequestEscalationView(
        UUID paymentId,
        int attempts,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
