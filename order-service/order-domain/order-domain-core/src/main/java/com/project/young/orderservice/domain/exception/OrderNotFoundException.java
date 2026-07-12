package com.project.young.orderservice.domain.exception;

public class OrderNotFoundException extends OrderDomainException {

    public OrderNotFoundException(String message) {
        super(message);
    }
}
