package com.project.young.paymentservice.application.port.output;

import java.util.UUID;

/** Separate idempotency ledger for user-requested refunds, never shared with saga compensations. */
public interface CustomerRefundProcessingPort {

    boolean isProcessed(UUID refundId);

    boolean recordProcessed(UUID refundId, UUID paymentId, UUID orderId, String userId);
}
