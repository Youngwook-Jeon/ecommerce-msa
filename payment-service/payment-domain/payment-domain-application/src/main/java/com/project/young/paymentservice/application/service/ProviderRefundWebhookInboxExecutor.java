package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.port.output.ProviderRefundWebhookInboxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class ProviderRefundWebhookInboxExecutor {
    private static final Logger log = LoggerFactory.getLogger(ProviderRefundWebhookInboxExecutor.class);
    private final ProviderRefundWebhookInboxPort inbox;
    private final ProviderRefundObservationApplicationService observations;
    private final Clock clock;
    private final long leaseMs;
    private final int maxAttempts;
    private final long retryDelayMs;

    public ProviderRefundWebhookInboxExecutor(ProviderRefundWebhookInboxPort inbox,
            ProviderRefundObservationApplicationService observations, Clock clock,
            @Value("${payment-service.provider-refund-webhook-inbox.lease-ms:300000}") long leaseMs,
            @Value("${payment-service.provider-refund-webhook-inbox.max-attempts:20}") int maxAttempts,
            @Value("${payment-service.provider-refund-webhook-inbox.retry-delay-ms:30000}") long retryDelayMs) {
        this.inbox = inbox;
        this.observations = observations;
        this.clock = clock;
        this.leaseMs = Math.max(1, leaseMs);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMs = Math.max(1, retryDelayMs);
    }

    @Scheduled(fixedDelayString = "${payment-service.provider-refund-webhook-inbox.fixed-delay-ms:1000}")
    public void reconcile() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant expiredBefore = now.minusMillis(leaseMs);
        for (var command : inbox.findReady(100, now, expiredBefore)) {
            if (!inbox.claim(command.eventId(), now, expiredBefore)) {
                continue;
            }
            try {
                if (observations.observe(command)) {
                    inbox.markApplied(command.eventId(), now);
                } else {
                    defer(command.eventId(), now, "UnmatchedRefundClaim");
                }
            } catch (RuntimeException ex) {
                log.warn("Refund webhook processing deferred eventId={} providerRefundId={} exceptionClass={}",
                        command.eventId(), command.providerRefundId(), ex.getClass().getName());
                defer(command.eventId(), now, ex.getClass().getName());
            }
        }
    }

    private void defer(String eventId, Instant claimedAt, String exceptionClass) {
        inbox.retryOrEscalate(eventId, claimedAt, maxAttempts, clock.instant().plusMillis(retryDelayMs), exceptionClass);
        log.warn("Refund webhook queued for retry or review eventId={} exceptionClass={}", eventId, exceptionClass);
    }
}
