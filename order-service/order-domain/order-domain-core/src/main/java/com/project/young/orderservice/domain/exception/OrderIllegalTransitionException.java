package com.project.young.orderservice.domain.exception;

/**
 * Thrown when an order status transition is illegal for the current terminal/non-pending state
 * (e.g. confirm after CANCELLED). Not recoverable by retrying the same command.
 */
public class OrderIllegalTransitionException extends OrderDomainException {

    public OrderIllegalTransitionException(String message) {
        super(message);
    }
}
