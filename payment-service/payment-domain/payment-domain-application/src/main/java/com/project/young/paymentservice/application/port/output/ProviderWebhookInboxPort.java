package com.project.young.paymentservice.application.port.output;

import com.project.young.paymentservice.application.dto.command.ApplyProviderPaymentResultCommand;
import com.project.young.paymentservice.application.dto.query.ProviderWebhookInboxEscalationView;

import java.time.Instant;
import java.util.List;

/** Durable inbox for verified provider webhooks, independent from payment settlement idempotency. */
public interface ProviderWebhookInboxPort {

    void recordReceived(ApplyProviderPaymentResultCommand command);

    List<ApplyProviderPaymentResultCommand> findReady(int limit);

    boolean claim(String eventId, Instant startedAt);

    void markApplied(String eventId);

    void returnToWaiting(String eventId, String failureMessage, Instant nextRetryAt);

    void escalate(String eventId, String failureMessage);

    int returnExpiredProcessingToWaiting(Instant threshold);

    boolean hasReachedAttemptLimit(String eventId, int maxAttempts);

    int attempts(String eventId);

    List<ProviderWebhookInboxEscalationView> findEscalated(int limit);
}
