package com.project.young.productservice.domain.exception;

public class AttributeInactiveException extends RuntimeException {
    public AttributeInactiveException() {
        super("Inactive attributes cannot be used for new values, enabled category policies, or option group activation");
    }
}
