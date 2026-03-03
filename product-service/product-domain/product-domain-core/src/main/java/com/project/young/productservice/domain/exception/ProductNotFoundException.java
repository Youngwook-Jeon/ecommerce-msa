package com.project.young.productservice.domain.exception;

public class ProductNotFoundException extends ProductDomainException {
    public ProductNotFoundException(String message) {
        super(message);
    }

    public ProductNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
