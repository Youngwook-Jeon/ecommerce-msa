package com.project.young.productservice.domain.exception;

import com.project.young.common.domain.exception.DomainException;

public class ProductAlreadyExistsException extends DomainException {

    public ProductAlreadyExistsException(String message) {
        super(message);
    }

    public ProductAlreadyExistsException(String message, Throwable cause) { super(message, cause); }
}
