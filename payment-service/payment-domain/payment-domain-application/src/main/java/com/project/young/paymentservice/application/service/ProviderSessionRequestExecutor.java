package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.port.output.ProviderSessionRequestPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.time.Clock;

@Component
public class ProviderSessionRequestExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProviderSessionRequestExecutor.class);
    private final ProviderSessionRequestPort requests;
    private final PaymentApplicationService payments;
    private final Clock clock;
    private final long leaseMs;
    private final int maxAttempts;

    public ProviderSessionRequestExecutor(ProviderSessionRequestPort requests, PaymentApplicationService payments, Clock clock,
            @Value("${payment-service.provider-session-request.lease-ms:300000}") long leaseMs,
            @Value("${payment-service.provider-session-request.max-attempts:5}") int maxAttempts) {
        this.requests = requests;
        this.payments = payments;
        this.clock = clock; this.leaseMs = leaseMs; this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Scheduled(fixedDelayString = "${payment-service.provider-session-request.fixed-delay-ms:1000}")
    public void executePending() {
        int recovered = requests.releaseExpiredProcessing(clock.instant().minusMillis(leaseMs));
        if (recovered > 0) log.warn("Recovered expired provider-session request lease(s) count={}", recovered);
        for (var paymentId : requests.claimPending(100)) {
            try {
                payments.createProviderSession(paymentId);
                requests.complete(paymentId);
                log.info("Completed provider session request paymentId={}", paymentId);
            } catch (RuntimeException ex) {
                if (requests.hasReachedAttemptLimit(paymentId, maxAttempts)) {
                    requests.escalate(paymentId, ex.getMessage());
                    log.error("Escalated provider session request after maximum attempts paymentId={}", paymentId, ex);
                } else {
                    requests.release(paymentId, ex.getMessage());
                    log.warn("Provider session request failed; released for retry paymentId={}", paymentId, ex);
                }
            }
        }
    }
}
