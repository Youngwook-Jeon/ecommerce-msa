package com.project.young.orderservice.domain.exception;

/**
 * Thrown when an optimistic status update lost a concurrent compare-and-set.
 * Often recoverable by retrying the saga step.
 */
public class OrderStateConflictException extends OrderDomainException {

    public OrderStateConflictException(String message) {
        super(message);
    }
}
