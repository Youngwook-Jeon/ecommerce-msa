package com.project.young.orderservice.dataaccess.adapter.inventory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryReserveResponse(
    UUID checkoutId,
    Instant expiresAt,
    boolean reusedExisting,
    List<InventoryReserveLineResponse> lines
) {
  public InventoryReserveResponse {
    lines = lines == null ? List.of() : List.copyOf(lines);
  }
}
