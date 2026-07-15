package com.project.young.orderservice.application.port.output;

/**
 * Thrown when product-service rejects an inventory request with a 4xx other than insufficient
 * stock (409). Excluded from circuit breaker failure metrics.
 */
public class InventoryReservationClientException extends RuntimeException {

  public InventoryReservationClientException(String message) {
    super(message);
  }
}
