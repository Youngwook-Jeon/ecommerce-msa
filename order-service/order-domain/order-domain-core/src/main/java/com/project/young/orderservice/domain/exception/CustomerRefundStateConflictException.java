package com.project.young.orderservice.domain.exception;

public class CustomerRefundStateConflictException extends CustomerRefundDomainException {

    public CustomerRefundStateConflictException(String message) {
        super(message);
    }
}
