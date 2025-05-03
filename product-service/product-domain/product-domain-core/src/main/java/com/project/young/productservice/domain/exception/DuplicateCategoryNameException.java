package com.project.young.productservice.domain.exception;

public class DuplicateCategoryNameException extends CategoryDomainException {
    public DuplicateCategoryNameException(String message) {
        super(message);
    }

    public DuplicateCategoryNameException(String message, Throwable cause) {
        super(message, cause);
    }
}
