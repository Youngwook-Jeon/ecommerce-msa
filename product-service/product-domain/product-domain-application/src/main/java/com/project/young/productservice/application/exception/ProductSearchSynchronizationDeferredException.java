package com.project.young.productservice.application.exception;

/** Normal waiting state; the Kafka record must remain pending and must not be dead-lettered. */
public class ProductSearchSynchronizationDeferredException extends RuntimeException {
    public ProductSearchSynchronizationDeferredException(String message) { super(message); }
}
