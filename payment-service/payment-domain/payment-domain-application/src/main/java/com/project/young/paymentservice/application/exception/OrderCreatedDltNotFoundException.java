package com.project.young.paymentservice.application.exception;

import java.util.UUID;

public class OrderCreatedDltNotFoundException extends RuntimeException {

    public OrderCreatedDltNotFoundException(UUID eventId) {
        super("order.created DLT record not found: " + eventId);
    }
}
