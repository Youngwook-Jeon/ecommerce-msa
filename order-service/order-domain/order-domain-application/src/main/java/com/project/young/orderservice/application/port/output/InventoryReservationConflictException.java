package com.project.young.orderservice.application.port.output;

/**
 * Thrown when product-service returns 409 because available stock is insufficient.
 * Business outcome, not a downstream outage — excluded from circuit breaker failure metrics.
 */
public class InventoryReservationConflictException extends RuntimeException {

  public InventoryReservationConflictException(String message) {
    super(message);
  }
}
