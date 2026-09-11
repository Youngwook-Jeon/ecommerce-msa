package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.ApplyProviderPaymentResultCommand;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Detects terminal PSP state when a webhook was never delivered. The synthetic event id is stable
 * per provider payment and terminal outcome, so the regular provider-event idempotency rule applies.
 */
@Component
public class ProviderPaymentReconciliationExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProviderPaymentReconciliationExecutor.class);

    private final PaymentRepository paymentRepository;
    private final PaymentProviderPort paymentProvider;
    private final PaymentApplicationService payments;
    private final Clock clock;
    private final long pendingAgeMs;

    public ProviderPaymentReconciliationExecutor(
            PaymentRepository paymentRepository,
            PaymentProviderPort paymentProvider,
            PaymentApplicationService payments,
            Clock clock,
            @Value("${payment-service.provider-payment-reconciliation.pending-age-ms:300000}") long pendingAgeMs
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentProvider = paymentProvider;
        this.payments = payments;
        this.clock = clock;
        this.pendingAgeMs = Math.max(1, pendingAgeMs);
    }

    @Scheduled(fixedDelayString = "${payment-service.provider-payment-reconciliation.fixed-delay-ms:60000}")
    public void reconcilePendingPayments() {
        for (Payment payment : paymentRepository.findPendingWithProviderSessionUpdatedBefore(
                clock.instant().minusMillis(pendingAgeMs), 100)) {
            try {
                paymentProvider.retrieveTerminalResult(payment).ifPresent(result -> {
                    boolean applied = payments.applyProviderPaymentResult(toCommand(payment, result));
                    log.info(
                            "Reconciled missing provider webhook paymentId={} provider={} providerPaymentId={} outcome={} applied={}",
                            payment.getId().getValue(),
                            payment.getProvider(),
                            payment.getProviderPaymentId(),
                            result.outcome(),
                            applied
                    );
                });
            } catch (RuntimeException ex) {
                log.warn(
                        "Provider payment reconciliation failed paymentId={} provider={} providerPaymentId={}",
                        payment.getId().getValue(), payment.getProvider(), payment.getProviderPaymentId(), ex
                );
            }
        }
    }

    private static ApplyProviderPaymentResultCommand toCommand(
            Payment payment,
            PaymentProviderPort.ProviderPaymentResult result
    ) {
        String eventId = "reconciliation:" + payment.getProvider().name() + ":"
                + payment.getProviderPaymentId() + ":" + result.outcome().name();
        return switch (result.outcome()) {
            case SUCCEEDED -> ApplyProviderPaymentResultCommand.succeeded(
                    eventId, payment.getProvider(), payment.getProviderPaymentId());
            case FINAL_FAILED -> ApplyProviderPaymentResultCommand.finalFailure(
                    eventId, payment.getProvider(), payment.getProviderPaymentId(), result.failureReason());
            case ATTEMPT_FAILED -> throw new IllegalArgumentException("Attempt failure is not terminal");
        };
    }
}
