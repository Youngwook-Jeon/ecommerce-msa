package com.project.young.productservice.domain.exception;

import com.project.young.common.domain.exception.DomainException;

public class ProductNotFoundException extends DomainException {
    public ProductNotFoundException(String message) {
        super(message);
    }

    public ProductNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
