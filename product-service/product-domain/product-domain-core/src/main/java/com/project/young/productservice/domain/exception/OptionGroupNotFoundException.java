package com.project.young.productservice.domain.exception;

public class OptionGroupNotFoundException extends ProductDomainException {
    public OptionGroupNotFoundException(String message) {
        super(message);
    }

    public OptionGroupNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
