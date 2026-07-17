package com.project.young.orderservice.domain.exception;

/**
 * Thrown when an order status transition is illegal or lost a concurrent compare-and-set.
 */
public class OrderStateConflictException extends OrderDomainException {

    public OrderStateConflictException(String message) {
        super(message);
    }
}
