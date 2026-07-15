package com.project.young.common.domain.valueobject;

import java.util.UUID;

/**
 * Correlation id for a checkout attempt (saga / order placement).
 * Used as the idempotency key for soft-hold inventory reservations.
 */
public class CheckoutId extends BaseId<UUID> {

    public CheckoutId(UUID value) {
        super(value);
    }
}
