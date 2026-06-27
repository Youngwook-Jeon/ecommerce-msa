package com.project.young.orderservice.application.port.output;

/**
 * Thrown when the product catalog cannot be resolved because the downstream product-service
 * call failed or the circuit breaker is open. Callers must treat this as a transient failure
 * and must NOT mutate the cart (e.g. never interpret it as "all lines unavailable").
 */
public class ProductCatalogUnavailableException extends RuntimeException {

    public ProductCatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
