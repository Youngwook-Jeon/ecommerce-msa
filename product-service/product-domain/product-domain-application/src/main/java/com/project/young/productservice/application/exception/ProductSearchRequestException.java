package com.project.young.productservice.application.exception;

public class ProductSearchRequestException extends IllegalArgumentException {

    public enum Code {
        INVALID_SEARCH_REQUEST,
        SEARCH_PAGE_LIMIT_EXCEEDED
    }

    private final Code code;

    public ProductSearchRequestException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
