package com.project.young.productservice.application.port.output;

import java.util.UUID;

/**
 * Durable idempotency store for successfully applied inventory-release compensations.
 */
public interface InventoryReleaseCompensationPort {

    boolean isProcessed(UUID compensationEventId);

    /**
     * Records a completed release once.
     *
     * @return {@code true} when this invocation recorded the compensation, {@code false} when it was already recorded
     */
    boolean recordProcessed(UUID compensationEventId, UUID orderId);
}
