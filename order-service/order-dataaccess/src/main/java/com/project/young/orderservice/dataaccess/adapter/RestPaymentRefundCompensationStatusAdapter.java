package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.port.output.PaymentReconciliationClientException;
import com.project.young.orderservice.application.port.output.PaymentReconciliationUnavailableException;
import com.project.young.orderservice.application.port.output.PaymentRefundCompensationStatusPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class RestPaymentRefundCompensationStatusAdapter implements PaymentRefundCompensationStatusPort {

    private static final String CIRCUIT_BREAKER_ID = "paymentRefundReconciliation";

    private final RestClient paymentReconciliationRestClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public RestPaymentRefundCompensationStatusAdapter(
            @Qualifier("paymentReconciliationRestClient") RestClient paymentReconciliationRestClient,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory
    ) {
        this.paymentReconciliationRestClient = paymentReconciliationRestClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    @Override
    public boolean isProcessed(UUID compensationEventId) {
        CircuitBreaker circuitBreaker = circuitBreakerFactory.create(CIRCUIT_BREAKER_ID);
        return circuitBreaker.run(
                () -> paymentReconciliationRestClient.get()
                        .uri("/internal/refund-compensations/{compensationEventId}", compensationEventId)
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
            throw new PaymentReconciliationClientException(
                    "Payment reconciliation API rejected status lookup with status " + status);
        }
        throw new PaymentReconciliationUnavailableException(
                "Payment reconciliation API returned status " + status,
                null
        );
    }

    private boolean handleFallback(Throwable throwable) {
        if (throwable instanceof PaymentReconciliationClientException clientException) {
            throw clientException;
        }
        if (throwable instanceof PaymentReconciliationUnavailableException unavailableException) {
            throw unavailableException;
        }
        throw new PaymentReconciliationUnavailableException(
                "Payment reconciliation API is currently unavailable.",
                throwable
        );
    }
}
