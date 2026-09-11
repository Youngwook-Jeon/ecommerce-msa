package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.ApplyProviderPaymentResultCommand;
import com.project.young.paymentservice.application.port.output.ProviderWebhookInboxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Reconciles verified webhooks that can arrive before their provider session is persisted. */
@Component
public class ProviderWebhookInboxExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProviderWebhookInboxExecutor.class);

    private final ProviderWebhookInboxPort inbox;
    private final PaymentApplicationService payments;
    private final Clock clock;
    private final long leaseMs;
    private final int maxAttempts;
    private final long initialRetryDelayMs;
    private final long maxRetryDelayMs;

    public ProviderWebhookInboxExecutor(
            ProviderWebhookInboxPort inbox,
            PaymentApplicationService payments,
            Clock clock,
            @Value("${payment-service.provider-webhook-inbox.lease-ms:300000}") long leaseMs,
            @Value("${payment-service.provider-webhook-inbox.max-attempts:20}") int maxAttempts,
            @Value("${payment-service.provider-webhook-inbox.initial-retry-delay-ms:5000}") long initialRetryDelayMs,
            @Value("${payment-service.provider-webhook-inbox.max-retry-delay-ms:300000}") long maxRetryDelayMs
    ) {
        this.inbox = inbox;
        this.payments = payments;
        this.clock = clock;
        this.leaseMs = leaseMs;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialRetryDelayMs = Math.max(1, initialRetryDelayMs);
        this.maxRetryDelayMs = Math.max(this.initialRetryDelayMs, maxRetryDelayMs);
    }

    @Scheduled(fixedDelayString = "${payment-service.provider-webhook-inbox.fixed-delay-ms:1000}")
    public void reconcile() {
        int recovered = inbox.returnExpiredProcessingToWaiting(clock.instant().minusMillis(leaseMs));
        if (recovered > 0) {
            log.warn("Recovered expired provider-webhook inbox lease(s) count={}", recovered);
        }

        for (ApplyProviderPaymentResultCommand command : inbox.findReady(100)) {
            if (!inbox.claim(command.eventId(), clock.instant())) {
                continue;
            }
            try {
                if (!payments.hasProviderPayment(command)) {
                    returnToWaitingOrEscalate(command, "Payment is not yet associated with provider payment id");
                    continue;
                }

                boolean applied = payments.applyProviderPaymentResult(command);
                inbox.markApplied(command.eventId());
                log.info(
                        "Reconciled provider webhook inbox eventId={} provider={} providerPaymentId={} applied={}",
                        command.eventId(), command.provider(), command.providerPaymentId(), applied
                );
            } catch (RuntimeException ex) {
                returnToWaitingOrEscalate(command, ex.getMessage());
            }
        }
    }

    private void returnToWaitingOrEscalate(ApplyProviderPaymentResultCommand command, String failureMessage) {
        if (inbox.hasReachedAttemptLimit(command.eventId(), maxAttempts)) {
            inbox.escalate(command.eventId(), failureMessage);
            log.error(
                    "Escalated provider-webhook inbox eventId={} provider={} providerPaymentId={} after maximum attempts",
                    command.eventId(), command.provider(), command.providerPaymentId()
            );
            return;
        }
        inbox.returnToWaiting(command.eventId(), failureMessage, nextRetryAt(inbox.attempts(command.eventId())));
        log.warn(
                "Provider webhook inbox reconciliation deferred eventId={} provider={} providerPaymentId={}",
                command.eventId(), command.provider(), command.providerPaymentId()
        );
    }

    private java.time.Instant nextRetryAt(int attempts) {
        // The claim increments attempts before this calculation. Cap the shift to avoid overflow.
        long multiplier = 1L << Math.clamp(attempts - 1, 0, 20);
        long delay = initialRetryDelayMs > maxRetryDelayMs / multiplier
                ? maxRetryDelayMs
                : Math.min(maxRetryDelayMs, initialRetryDelayMs * multiplier);
        return clock.instant().plusMillis(delay);
    }
}
