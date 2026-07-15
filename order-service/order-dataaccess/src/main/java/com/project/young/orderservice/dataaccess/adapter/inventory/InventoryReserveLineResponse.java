package com.project.young.orderservice.dataaccess.adapter.inventory;

import java.util.UUID;

public record InventoryReserveLineResponse(
    UUID reservationId,
    UUID productVariantId,
    int quantity,
    String status
) {
}
