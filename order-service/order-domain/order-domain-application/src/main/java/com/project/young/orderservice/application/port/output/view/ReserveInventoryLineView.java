package com.project.young.orderservice.application.port.output.view;

import java.util.UUID;

public record ReserveInventoryLineView(UUID productVariantId, int quantity) {
}
