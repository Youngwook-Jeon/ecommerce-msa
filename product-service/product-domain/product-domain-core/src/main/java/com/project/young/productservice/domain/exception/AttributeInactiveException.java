package com.project.young.productservice.domain.exception;

public class AttributeInactiveException extends RuntimeException {
    public AttributeInactiveException() {
        super("Inactive attributes cannot be used for new values or enabled category policies");
    }
}
