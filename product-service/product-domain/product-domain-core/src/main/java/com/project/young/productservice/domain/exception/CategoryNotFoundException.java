package com.project.young.productservice.domain.exception;

public class CategoryNotFoundException extends CategoryDomainException {
    public CategoryNotFoundException(String message) {
        super(message);
    }

    public CategoryNotFoundException(String message, Throwable cause) {
      super(message, cause);
    }
}
