package com.project.young.orderservice.application.port.output;

/**
 * Thrown when inventory reservation cannot be completed because product-service is down,
 * slow, rate-limited, or the circuit breaker is open.
 */
public class InventoryReservationUnavailableException extends RuntimeException {

  public InventoryReservationUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
