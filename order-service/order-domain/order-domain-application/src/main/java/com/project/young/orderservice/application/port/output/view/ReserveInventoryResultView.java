package com.project.young.orderservice.application.port.output.view;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReserveInventoryResultView(
    UUID checkoutId,
    Instant expiresAt,
    boolean reusedExisting,
    List<ReserveInventoryLineResultView> lines
) {
  public ReserveInventoryResultView {
    lines = lines == null ? List.of() : List.copyOf(lines);
  }
}
