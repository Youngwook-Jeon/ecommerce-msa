package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.port.output.InventoryReservationClientException;
import com.project.young.orderservice.application.port.output.InventoryReservationConflictException;
import com.project.young.orderservice.application.port.output.InventoryReservationPort;
import com.project.young.orderservice.application.port.output.InventoryReservationUnavailableException;
import com.project.young.orderservice.application.port.output.view.ReserveInventoryLineResultView;
import com.project.young.orderservice.application.port.output.view.ReserveInventoryLineView;
import com.project.young.orderservice.application.port.output.view.ReserveInventoryResultView;
import com.project.young.orderservice.dataaccess.adapter.inventory.InventoryReserveLineRequest;
import com.project.young.orderservice.dataaccess.adapter.inventory.InventoryReserveLineResponse;
import com.project.young.orderservice.dataaccess.adapter.inventory.InventoryReserveRequest;
import com.project.young.orderservice.dataaccess.adapter.inventory.InventoryReserveResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@Slf4j
public class RestInventoryReservationAdapter implements InventoryReservationPort {

  private static final String RESERVATIONS_PATH = "/internal/inventory/reservations";
  private static final String CONFIRM_PATH = "/internal/inventory/reservations/{checkoutId}/confirm";
  private static final String RELEASE_PATH = "/internal/inventory/reservations/{checkoutId}/release";
  private static final String CIRCUIT_BREAKER_ID = "inventoryReservation";
  private static final int MAX_LINES = 50;

  private final RestClient inventoryReservationRestClient;
  private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

  public RestInventoryReservationAdapter(
      @Qualifier("inventoryReservationRestClient") RestClient inventoryReservationRestClient,
      CircuitBreakerFactory<?, ?> circuitBreakerFactory
  ) {
    this.inventoryReservationRestClient = inventoryReservationRestClient;
    this.circuitBreakerFactory = circuitBreakerFactory;
  }

  @Override
  public ReserveInventoryResultView reserve(UUID checkoutId, List<ReserveInventoryLineView> lines) {
    Objects.requireNonNull(checkoutId, "checkoutId must not be null");
    if (lines == null || lines.isEmpty()) {
      throw new IllegalArgumentException("Reserve request must contain at least one line.");
    }
    if (lines.size() > MAX_LINES) {
      throw new IllegalArgumentException("Cannot reserve more than " + MAX_LINES + " lines at once.");
    }

    CircuitBreaker circuitBreaker = circuitBreakerFactory.create(CIRCUIT_BREAKER_ID);
    return circuitBreaker.run(
        () -> fetchReserve(checkoutId, lines),
        this::handleFallback
    );
  }

  @Override
  public void confirm(UUID checkoutId) {
    Objects.requireNonNull(checkoutId, "checkoutId must not be null");
    CircuitBreaker circuitBreaker = circuitBreakerFactory.create(CIRCUIT_BREAKER_ID);
    circuitBreaker.run(
        () -> {
          postWithoutBody(CONFIRM_PATH, checkoutId);
          return null;
        },
        this::handleFallback
    );
  }

  @Override
  public void release(UUID checkoutId) {
    Objects.requireNonNull(checkoutId, "checkoutId must not be null");
    CircuitBreaker circuitBreaker = circuitBreakerFactory.create(CIRCUIT_BREAKER_ID);
    circuitBreaker.run(
        () -> {
          postWithoutBody(RELEASE_PATH, checkoutId);
          return null;
        },
        this::handleFallback
    );
  }

  private ReserveInventoryResultView fetchReserve(UUID checkoutId, List<ReserveInventoryLineView> lines) {
    InventoryReserveRequest request = new InventoryReserveRequest(
        checkoutId,
        lines.stream()
            .map(line -> new InventoryReserveLineRequest(line.productVariantId(), line.quantity()))
            .toList()
    );

    InventoryReserveResponse response = inventoryReservationRestClient.post()
        .uri(RESERVATIONS_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .onStatus(
            status -> status.value() == HttpStatus.CONFLICT.value(),
            (req, clientResponse) -> {
              throw new InventoryReservationConflictException(
                  "Product inventory rejected the reserve request with status 409 Conflict.");
            })
        .onStatus(
            status -> status.is4xxClientError()
                && status.value() != HttpStatus.TOO_MANY_REQUESTS.value()
                && status.value() != HttpStatus.CONFLICT.value(),
            (req, clientResponse) -> {
              throw new InventoryReservationClientException(
                  "Product inventory rejected the reserve request with status "
                      + clientResponse.getStatusCode() + ".");
            })
        .body(InventoryReserveResponse.class);

    if (response == null) {
      throw new InventoryReservationUnavailableException(
          "Product inventory returned an empty reserve response.",
          null
      );
    }
    return toView(response);
  }

  private void postWithoutBody(String path, UUID checkoutId) {
    inventoryReservationRestClient.post()
        .uri(path, checkoutId)
        .retrieve()
        .onStatus(
            status -> status.is4xxClientError()
                && status.value() != HttpStatus.TOO_MANY_REQUESTS.value(),
            (req, clientResponse) -> {
              throw new InventoryReservationClientException(
                  "Product inventory rejected the request with status "
                      + clientResponse.getStatusCode() + ".");
            })
        .toBodilessEntity();
  }

  private static ReserveInventoryResultView toView(InventoryReserveResponse response) {
    List<ReserveInventoryLineResultView> lines = response.lines().stream()
        .map(RestInventoryReservationAdapter::toLineView)
        .toList();
    return new ReserveInventoryResultView(
        response.checkoutId(),
        response.expiresAt(),
        response.reusedExisting(),
        lines
    );
  }

  private static ReserveInventoryLineResultView toLineView(InventoryReserveLineResponse line) {
    return new ReserveInventoryLineResultView(
        line.reservationId(),
        line.productVariantId(),
        line.quantity(),
        line.status()
    );
  }

  private <T> T handleFallback(Throwable throwable) {
    log.error("Calling inventory reservation API has failed: {}", throwable.getMessage());
    throw new InventoryReservationUnavailableException(
        "Product inventory reservation is currently unavailable.",
        throwable
    );
  }
}
