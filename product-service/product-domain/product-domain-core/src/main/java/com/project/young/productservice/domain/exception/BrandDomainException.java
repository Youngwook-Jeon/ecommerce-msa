package com.project.young.productservice.domain.exception;

import com.project.young.common.domain.exception.DomainException;

public class BrandDomainException extends DomainException {
    public BrandDomainException(String message) {
        super(message);
    }

    public BrandDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
