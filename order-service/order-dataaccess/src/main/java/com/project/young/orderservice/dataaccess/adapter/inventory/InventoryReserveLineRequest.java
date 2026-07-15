package com.project.young.orderservice.dataaccess.adapter.inventory;

import java.util.List;
import java.util.UUID;

public record InventoryReserveLineRequest(UUID productVariantId, int quantity) {
}
