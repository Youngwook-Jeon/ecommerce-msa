package com.project.young.orderservice.domain.exception;

public class CartItemNotFoundException extends CartDomainException {

    public CartItemNotFoundException(String message) {
        super(message);
    }
}
