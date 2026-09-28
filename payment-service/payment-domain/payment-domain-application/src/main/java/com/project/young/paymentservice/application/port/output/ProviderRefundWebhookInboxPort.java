package com.project.young.paymentservice.application.port.output;

import com.project.young.paymentservice.application.dto.command.ObserveProviderRefundCommand;

import java.time.Instant;
import java.util.List;

public interface ProviderRefundWebhookInboxPort {
    boolean recordReceived(ObserveProviderRefundCommand command);

    List<ObserveProviderRefundCommand> findReady(int limit, Instant now, Instant expiredBefore);

    boolean claim(String eventId, Instant now, Instant expiredBefore);

    void markApplied(String eventId, Instant claimedAt);

    void retryOrEscalate(String eventId, Instant claimedAt, int maxAttempts, Instant nextAttemptAt, String exceptionClass);
}
