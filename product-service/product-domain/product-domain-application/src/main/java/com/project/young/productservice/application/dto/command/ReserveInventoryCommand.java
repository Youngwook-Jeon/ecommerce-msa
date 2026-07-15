package com.project.young.productservice.application.dto.command;

import java.util.List;
import java.util.UUID;

public record ReserveInventoryCommand(
        UUID checkoutId,
        List<ReserveInventoryLine> lines
) {
    public record ReserveInventoryLine(
            UUID productVariantId,
            int quantity
    ) {
    }
}
