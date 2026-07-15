package com.project.young.productservice.domain.exception;

import com.project.young.common.domain.exception.DomainException;

public class InventoryDomainException extends DomainException {

    public InventoryDomainException(String message) {
        super(message);
    }

    public InventoryDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
