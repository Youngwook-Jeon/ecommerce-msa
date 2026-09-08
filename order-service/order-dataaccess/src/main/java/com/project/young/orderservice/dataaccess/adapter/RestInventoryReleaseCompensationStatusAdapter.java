package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.port.output.InventoryReleaseCompensationStatusPort;
import com.project.young.orderservice.application.port.output.InventoryReservationClientException;
import com.project.young.orderservice.application.port.output.InventoryReservationUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class RestInventoryReleaseCompensationStatusAdapter implements InventoryReleaseCompensationStatusPort {

    private static final String CIRCUIT_BREAKER_ID = "inventoryReservation";

    private final RestClient inventoryReservationRestClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public RestInventoryReleaseCompensationStatusAdapter(
            @Qualifier("inventoryReservationRestClient") RestClient inventoryReservationRestClient,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory
    ) {
        this.inventoryReservationRestClient = inventoryReservationRestClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    @Override
    public boolean isProcessed(UUID compensationEventId) {
        CircuitBreaker circuitBreaker = circuitBreakerFactory.create(CIRCUIT_BREAKER_ID);
        return circuitBreaker.run(
                () -> inventoryReservationRestClient.get()
                        .uri("/internal/inventory/release-compensations/{compensationEventId}", compensationEventId)
                        .exchange((request, response) -> toProcessed(response.getStatusCode())),
                this::handleFallback
        );
    }

    private static boolean toProcessed(HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return false;
        }
        if (status.is2xxSuccessful()) {
            return true;
        }
        if (status.is4xxClientError()) {
            throw new InventoryReservationClientException(
                    "Product inventory reconciliation API rejected status lookup with status " + status);
        }
        throw new InventoryReservationUnavailableException(
                "Product inventory reconciliation API returned status " + status,
                null
        );
    }

    private boolean handleFallback(Throwable throwable) {
        if (throwable instanceof InventoryReservationClientException clientException) {
            throw clientException;
        }
        if (throwable instanceof InventoryReservationUnavailableException unavailableException) {
            throw unavailableException;
        }
        throw new InventoryReservationUnavailableException(
                "Product inventory reconciliation API is currently unavailable.",
                throwable
        );
    }
}
