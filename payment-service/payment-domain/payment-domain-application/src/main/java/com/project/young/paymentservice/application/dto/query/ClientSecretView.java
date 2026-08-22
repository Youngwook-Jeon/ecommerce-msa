package com.project.young.paymentservice.application.dto.query;

import java.util.UUID;

public record ClientSecretView(
        UUID paymentId,
        UUID orderId,
        String provider,
        String clientSecret,
        String status
) {
}
