package com.project.young.productservice.domain.exception;

import com.project.young.common.domain.exception.DomainException;

public class OptionDomainException extends DomainException {
    public OptionDomainException(String message) {
        super(message);
    }

    public OptionDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
