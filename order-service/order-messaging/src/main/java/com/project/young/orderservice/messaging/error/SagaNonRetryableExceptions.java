package com.project.young.orderservice.messaging.error;

import com.project.young.orderservice.application.port.output.InventoryReservationClientException;
import com.project.young.orderservice.application.port.output.InventoryReservationConflictException;
import com.project.young.orderservice.domain.exception.OrderCheckoutValidationException;
import com.project.young.orderservice.domain.exception.OrderIllegalTransitionException;
import com.project.young.orderservice.domain.exception.OrderNotFoundException;

import java.util.List;

/**
 * Exceptions that should skip Kafka retries and go straight to DLT for payment saga consumers.
 * Transient failures (inventory unavailable, CAS conflict) remain retryable by default.
 */
public final class SagaNonRetryableExceptions {

    private SagaNonRetryableExceptions() {
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends Exception>[] types() {
        List<Class<? extends Exception>> types = List.of(
                OrderNotFoundException.class,
                OrderIllegalTransitionException.class,
                OrderCheckoutValidationException.class,
                InventoryReservationClientException.class,
                InventoryReservationConflictException.class
        );
        return types.toArray(Class[]::new);
    }
}
