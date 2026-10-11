package com.project.young.productservice.dataaccess.search;

public class ProductSearchIndexingException extends RuntimeException {

    public ProductSearchIndexingException(String message) {
        super(message);
    }

    public ProductSearchIndexingException(String message, Throwable cause) {
        super(message, cause);
    }
}
