package com.project.young.productservice.domain.exception;

public class InsufficientInventoryException extends InventoryDomainException {

    public InsufficientInventoryException(String message) {
        super(message);
    }
}
