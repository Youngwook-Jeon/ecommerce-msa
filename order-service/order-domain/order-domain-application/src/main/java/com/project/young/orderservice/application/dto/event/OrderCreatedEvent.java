package com.project.young.orderservice.application.dto.event;

import com.project.young.common.domain.valueobject.Money;

import java.time.Instant;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        String userId,
        Money totalAmount,
        String currency,
        Instant occurredAt
) {
    public OrderCreatedEvent {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (totalAmount == null) {
            throw new IllegalArgumentException("totalAmount must not be null");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
