package com.project.young.productservice.domain.exception;

public class AttributeInUseException extends RuntimeException {
    public AttributeInUseException() {
        super("Attribute definition is referenced and cannot be deleted; deactivate it to preserve existing references");
    }
}
