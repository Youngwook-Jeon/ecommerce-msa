package com.project.young.paymentservice.application.port.output;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

public interface ProviderSessionRequestPort {

    void enqueue(UUID paymentId);

    List<UUID> claimPending(int limit);

    void complete(UUID paymentId);

    void release(UUID paymentId, String failureMessage);

    void escalate(UUID paymentId, String failureMessage);

    int releaseExpiredProcessing(Instant threshold);

    boolean hasReachedAttemptLimit(UUID paymentId, int maxAttempts);
}
