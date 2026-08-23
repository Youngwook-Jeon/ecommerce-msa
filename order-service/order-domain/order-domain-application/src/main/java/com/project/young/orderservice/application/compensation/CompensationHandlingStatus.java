package com.project.young.orderservice.application.compensation;

/**
 * Durable handling state. First slice always inserts {@link #MANUAL}.
 */
public enum CompensationHandlingStatus {
    MANUAL,
    REPLAYED,
    REFUNDED,
    CLOSED
}
