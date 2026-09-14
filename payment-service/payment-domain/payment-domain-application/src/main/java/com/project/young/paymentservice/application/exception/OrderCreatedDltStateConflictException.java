package com.project.young.paymentservice.application.exception;

import java.util.UUID;

public class OrderCreatedDltStateConflictException extends RuntimeException {

    public OrderCreatedDltStateConflictException(UUID eventId) {
        super("order.created DLT record cannot be operated in its current state: " + eventId);
    }
}
