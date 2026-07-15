package com.project.young.orderservice.application.port.output.view;

import java.util.UUID;

public record ReserveInventoryLineResultView(
    UUID reservationId,
    UUID productVariantId,
    int quantity,
    String status
) {
}
