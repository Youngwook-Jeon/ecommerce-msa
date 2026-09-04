package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.port.output.PaymentRefundPort;
import com.project.young.orderservice.application.port.output.PaymentRefundClientException;
import com.project.young.orderservice.application.port.output.PaymentRefundUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Component
public class RestPaymentRefundAdapter implements PaymentRefundPort {
    private static final Logger log = LoggerFactory.getLogger(RestPaymentRefundAdapter.class);
    private static final String CIRCUIT_BREAKER_ID = "paymentRefund";
    private final RestClient paymentRefundRestClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public RestPaymentRefundAdapter(
            @Qualifier("paymentRefundRestClient") RestClient paymentRefundRestClient,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory
    ) {
        this.paymentRefundRestClient = paymentRefundRestClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    @Override
    public void refund(UUID paymentId, UUID compensationEventId) {
        log.info("Requesting payment refund paymentId={} compensationEventId={}", paymentId, compensationEventId);
        CircuitBreaker circuitBreaker = circuitBreakerFactory.create(CIRCUIT_BREAKER_ID);
        circuitBreaker.run(() -> {
            paymentRefundRestClient.post()
                    .uri("/internal/payments/{paymentId}/refund", paymentId)
                    .body(new RefundRequest(compensationEventId))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() && status.value() != HttpStatus.TOO_MANY_REQUESTS.value(),
                            (request, response) -> { throw new PaymentRefundClientException("Payment service rejected refund with status " + response.getStatusCode()); }
                    )
                    .toBodilessEntity();
            return null;
        }, this::handleFallback);
        log.info("Payment refund accepted paymentId={} compensationEventId={}", paymentId, compensationEventId);
    }

    private <T> T handleFallback(Throwable throwable) {
        if (throwable instanceof PaymentRefundClientException clientException) {
            throw clientException;
        }
        if (throwable instanceof PaymentRefundUnavailableException unavailableException) {
            throw unavailableException;
        }
        log.warn("Payment refund API unavailable: {}", throwable.getMessage());
        throw new PaymentRefundUnavailableException("Payment refund is currently unavailable.", throwable);
    }

    private record RefundRequest(UUID compensationEventId) {
    }
}
