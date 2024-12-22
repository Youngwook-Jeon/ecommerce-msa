package com.project.young.productservice.domain.exception;

import com.project.young.common.domain.exception.DomainException;

public class ProductNotSavedException extends DomainException {

    public ProductNotSavedException(String message) {
        super(message);
    }

    public ProductNotSavedException(String message, Throwable cause) {
        super(message, cause);
    }
}
