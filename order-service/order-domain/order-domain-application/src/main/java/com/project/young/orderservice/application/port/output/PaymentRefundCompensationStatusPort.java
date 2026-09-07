package com.project.young.orderservice.application.port.output;

import java.util.UUID;

/**
 * Reads whether Payment Service has durably applied a refund compensation.
 */
public interface PaymentRefundCompensationStatusPort {

    boolean isProcessed(UUID compensationEventId);
}
