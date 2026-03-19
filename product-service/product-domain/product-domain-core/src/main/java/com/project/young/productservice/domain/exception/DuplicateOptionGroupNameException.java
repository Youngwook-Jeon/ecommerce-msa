package com.project.young.productservice.domain.exception;

public class DuplicateOptionGroupNameException extends CategoryDomainException {
    public DuplicateOptionGroupNameException(String message) {
        super(message);
    }

    public DuplicateOptionGroupNameException(String message, Throwable cause) {
        super(message, cause);
    }
}
