package com.project.young.productservice.web.internal.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReserveInventoryResponse(
        UUID checkoutId,
        Instant expiresAt,
        boolean reusedExisting,
        List<Line> lines
) {
    public record Line(
            UUID reservationId,
            UUID productVariantId,
            int quantity,
            String status
    ) {
    }
}
