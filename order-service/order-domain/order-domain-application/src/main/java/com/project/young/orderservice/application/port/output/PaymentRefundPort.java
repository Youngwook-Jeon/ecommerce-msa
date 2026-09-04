package com.project.young.orderservice.application.port.output;

import java.util.UUID;

/** Executes a provider refund using the compensation event id as its idempotency key. */
public interface PaymentRefundPort {

    void refund(UUID paymentId, UUID compensationEventId);
}
