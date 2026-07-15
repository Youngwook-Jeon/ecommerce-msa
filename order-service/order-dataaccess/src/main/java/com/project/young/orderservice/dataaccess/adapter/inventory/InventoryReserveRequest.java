package com.project.young.orderservice.dataaccess.adapter.inventory;

import java.util.List;
import java.util.UUID;

public record InventoryReserveRequest(UUID checkoutId, List<InventoryReserveLineRequest> lines) {

  public InventoryReserveRequest {
    lines = lines == null ? List.of() : List.copyOf(lines);
  }
}
