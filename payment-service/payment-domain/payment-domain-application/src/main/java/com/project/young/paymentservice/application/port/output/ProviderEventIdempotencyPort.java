package com.project.young.paymentservice.application.port.output;

import java.util.UUID;

/**
 * Marks provider webhook events as processed exactly once (Stripe event id, etc.).
 */
public interface ProviderEventIdempotencyPort {

    /**
     * @return {@code true} if this event id was newly recorded; {@code false} if already processed
     */
    boolean tryMarkProcessed(String eventId, UUID paymentId, String provider, String eventType);
}
