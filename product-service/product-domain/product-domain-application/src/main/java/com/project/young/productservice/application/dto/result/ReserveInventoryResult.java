package com.project.young.productservice.application.dto.result;

import com.project.young.productservice.domain.entity.InventoryReservation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReserveInventoryResult(
        UUID checkoutId,
        Instant expiresAt,
        List<Line> lines,
        boolean reusedExisting
) {
    public record Line(
            UUID reservationId,
            UUID productVariantId,
            int quantity,
            String status
    ) {
    }

    public static ReserveInventoryResult from(
            UUID checkoutId,
            Instant expiresAt,
            List<InventoryReservation> reservations,
            boolean reusedExisting
    ) {
        List<Line> lines = reservations.stream()
                .map(r -> new Line(
                        r.getId().getValue(),
                        r.getProductVariantId().getValue(),
                        r.getQuantity(),
                        r.getStatus().name()
                ))
                .toList();
        return new ReserveInventoryResult(checkoutId, expiresAt, lines, reusedExisting);
    }
}
