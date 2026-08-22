package com.project.young.paymentservice.application.port.input;

/**
 * Handles Stripe webhook payloads (signature verification is done by the implementing adapter).
 */
public interface StripeWebhookUseCase {

    void handle(String payload, String signatureHeader);
}
