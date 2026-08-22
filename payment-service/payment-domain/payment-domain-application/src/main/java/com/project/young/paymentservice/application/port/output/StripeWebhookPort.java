package com.project.young.paymentservice.application.port.output;

import com.project.young.paymentservice.application.dto.command.ApplyProviderPaymentResultCommand;

import java.util.Optional;

/**
 * Verifies and maps a raw Stripe webhook payload into a domain command.
 * Returns empty when the event type is intentionally ignored.
 */
public interface StripeWebhookPort {

    Optional<ApplyProviderPaymentResultCommand> verifyAndParse(String payload, String signatureHeader);
}
