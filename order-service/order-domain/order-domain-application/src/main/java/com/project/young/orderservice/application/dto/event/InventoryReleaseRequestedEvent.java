package com.project.young.orderservice.application.dto.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryReleaseRequestedEvent(
        UUID compensationEventId,
        UUID orderId,
        String reason,
        Instant occurredAt
) {
}
