package com.project.young.paymentservice.application.port.output;

import java.util.UUID;

/**
 * Durable idempotency store for successfully applied saga refund compensations.
 */
public interface RefundCompensationPort {

    boolean isProcessed(UUID compensationEventId);

    /**
     * Records a successful provider refund once.
     *
     * @return {@code true} when this invocation recorded the compensation, {@code false} when it was already recorded
     */
    boolean recordProcessed(UUID compensationEventId, UUID paymentId, UUID orderId);
}
