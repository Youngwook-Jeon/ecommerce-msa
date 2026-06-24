package com.project.young.orderservice.domain.exception;

public class CartNotFoundException extends CartDomainException {

    public CartNotFoundException(String message) {
        super(message);
    }
}
