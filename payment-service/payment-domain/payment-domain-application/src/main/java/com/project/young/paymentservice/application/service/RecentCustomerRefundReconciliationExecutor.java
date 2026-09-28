package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.ObserveProviderRefundCommand;
import com.project.young.paymentservice.application.port.output.PaymentRefundClaimPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundState;
import com.project.young.paymentservice.domain.repository.PaymentRepository;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/** Optional bounded safety net, not a guarantee of financial finality after the lookback window. */
@Component
@ConditionalOnProperty(prefix = "payment-service.recent-refund-reconciliation", name = "enabled", havingValue = "true")
public class RecentCustomerRefundReconciliationExecutor {
    private static final Logger log = LoggerFactory.getLogger(RecentCustomerRefundReconciliationExecutor.class);
    private final PaymentRefundClaimPort claims;
    private final PaymentRepository payments;
    private final ProviderRefundObservationApplicationService observations;
    private final Clock clock;
    private final int lookbackDays;
    private final long checkIntervalMs;
    private final int batchSize;

    public RecentCustomerRefundReconciliationExecutor(PaymentRefundClaimPort claims, PaymentRepository payments,
            ProviderRefundObservationApplicationService observations, Clock clock,
            @Value("${payment-service.recent-refund-reconciliation.lookback-days:7}") int lookbackDays,
            @Value("${payment-service.recent-refund-reconciliation.check-interval-ms:21600000}") long checkIntervalMs,
            @Value("${payment-service.recent-refund-reconciliation.batch-size:100}") int batchSize) {
        this.claims = claims;
        this.payments = payments;
        this.observations = observations;
        this.clock = clock;
        this.lookbackDays = Math.max(1, lookbackDays);
        this.checkIntervalMs = Math.max(1, checkIntervalMs);
        this.batchSize = Math.clamp(batchSize, 1, 100);
    }

    @Scheduled(fixedDelayString = "${payment-service.recent-refund-reconciliation.fixed-delay-ms:60000}")
    public void reconcile() {
        var now = clock.instant();
        for (var claim : claims.findRecentCustomerSuccesses(now.minus(Duration.ofDays(lookbackDays)),
                now.minusMillis(checkIntervalMs), batchSize)) {
            try {
                var payment = payments.findById(new PaymentId(claim.paymentId())).orElseThrow();
                if (payment.getProvider() == PaymentProvider.STRIPE) {
                    observations.observe(new ObserveProviderRefundCommand("reconciliation:" + claim.requestId(),
                            claim.providerRefundId(), payment.getProviderPaymentId(), RefundState.SUCCEEDED, null));
                }
            } catch (RuntimeException ex) {
                log.warn("Recent refund reconciliation deferred paymentId={} refundId={} exceptionClass={}",
                        claim.paymentId(), claim.requestId(), ex.getClass().getName());
            }
        }
    }
}
