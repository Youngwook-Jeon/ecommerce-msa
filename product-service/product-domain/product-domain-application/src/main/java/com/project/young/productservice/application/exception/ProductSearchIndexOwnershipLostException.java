package com.project.young.productservice.application.exception;

public class ProductSearchIndexOwnershipLostException extends RuntimeException {
    public ProductSearchIndexOwnershipLostException() {
        super("Index build ownership is no longer valid");
    }
}
