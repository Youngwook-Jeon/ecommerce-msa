package com.project.young.orderservice.domain.exception;

import com.project.young.common.domain.exception.DomainException;

public class OrderDomainException extends DomainException {

    public OrderDomainException(String message) {
        super(message);
    }
}
