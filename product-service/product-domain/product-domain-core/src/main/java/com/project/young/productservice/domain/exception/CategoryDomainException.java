package com.project.young.productservice.domain.exception;

import com.project.young.common.domain.exception.DomainException;

public class CategoryDomainException extends DomainException {
    public CategoryDomainException(String message) {
        super(message);
    }

    public CategoryDomainException(String message, Throwable cause) {
      super(message, cause);
    }
}
