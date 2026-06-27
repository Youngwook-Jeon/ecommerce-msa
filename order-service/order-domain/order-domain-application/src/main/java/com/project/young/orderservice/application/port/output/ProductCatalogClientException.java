package com.project.young.orderservice.application.port.output;

/**
 * Thrown when the product-service rejects our request with a 4xx client error (e.g. 400 Bad
 * Request). This signals a bug on the caller side, not a downstream outage, so it must be
 * excluded from circuit breaker failure metrics and must NOT trigger the unavailable fallback.
 */
public class ProductCatalogClientException extends RuntimeException {

    public ProductCatalogClientException(String message) {
        super(message);
    }
}
