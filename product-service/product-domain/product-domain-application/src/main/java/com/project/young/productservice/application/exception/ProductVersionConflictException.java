package com.project.young.productservice.application.exception;

import java.util.UUID;

public class ProductVersionConflictException extends RuntimeException {
    public ProductVersionConflictException(UUID productId, long expectedVersion) {
        super("Product was changed by another request. Reload before editing: productId=" + productId
                + ", expectedVersion=" + expectedVersion);
    }
}
