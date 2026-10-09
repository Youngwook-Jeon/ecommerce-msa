package com.project.young.productservice.application.exception;

public class ProductSearchUnavailableException extends RuntimeException {

    public ProductSearchUnavailableException(String message) {
        super(message);
    }

    public ProductSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
