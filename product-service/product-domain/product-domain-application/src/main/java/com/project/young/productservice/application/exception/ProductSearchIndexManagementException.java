package com.project.young.productservice.application.exception;

public class ProductSearchIndexManagementException extends RuntimeException {
    public enum Code {
        INVALID_INDEX_MANAGEMENT_REQUEST, INDEX_BUILD_NOT_FOUND, INDEX_BUILD_IN_PROGRESS,
        IDEMPOTENCY_KEY_CONFLICT, INDEX_ACTIVATION_IN_PROGRESS, INDEX_MANAGEMENT_UNAVAILABLE,
        INDEX_VERSION_NOT_FOUND, INDEX_ACTIVATION_NOT_FOUND, INDEX_NOT_READY, ACTIVE_INDEX_CHANGED, INDEX_STATE_UNCONFIRMED
    }

    private final Code code;

    public ProductSearchIndexManagementException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public ProductSearchIndexManagementException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
