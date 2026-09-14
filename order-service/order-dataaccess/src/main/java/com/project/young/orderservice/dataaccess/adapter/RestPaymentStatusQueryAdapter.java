package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.common.application.contract.payment.PaymentReconciliationStatus;
import com.project.young.orderservice.application.dto.PaymentStatusSnapshot;
import com.project.young.orderservice.application.port.output.PaymentReconciliationClientException;
import com.project.young.orderservice.application.port.output.PaymentReconciliationUnavailableException;
import com.project.young.orderservice.application.port.output.PaymentStatusQueryPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RestPaymentStatusQueryAdapter implements PaymentStatusQueryPort {

    private static final String CIRCUIT_BREAKER_ID = "paymentOrderStatusReconciliation";
    private static final int MAX_BATCH_SIZE = 100;
    private static final String PAYMENT_STATUS_PATH = "/internal/orders/payment-statuses";

    private final RestClient paymentReconciliationRestClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public RestPaymentStatusQueryAdapter(
            @Qualifier("paymentReconciliationRestClient") RestClient paymentReconciliationRestClient,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory
    ) {
        this.paymentReconciliationRestClient = paymentReconciliationRestClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    @Override
    public Map<UUID, PaymentStatusSnapshot> findByOrderIds(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinctOrderIds = new LinkedHashSet<>(orderIds).stream().toList();
        if (distinctOrderIds.contains(null)) {
            throw new IllegalArgumentException("orderIds must not contain null");
        }
        if (distinctOrderIds.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Cannot reconcile more than " + MAX_BATCH_SIZE + " payment statuses at once.");
        }

        CircuitBreaker circuitBreaker = circuitBreakerFactory.create(CIRCUIT_BREAKER_ID);
        return circuitBreaker.run(() -> fetch(distinctOrderIds), this::handleFallback);
    }

    private Map<UUID, PaymentStatusSnapshot> fetch(List<UUID> orderIds) {
        PaymentStatusBatchResponse response = paymentReconciliationRestClient.post()
                .uri(PAYMENT_STATUS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PaymentStatusBatchRequest(orderIds))
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError()
                                && status.value() != HttpStatus.TOO_MANY_REQUESTS.value(),
                        (request, clientResponse) -> {
                            throw new PaymentReconciliationClientException(
                                    "Payment status reconciliation API rejected batch lookup with status "
                                            + clientResponse.getStatusCode());
                        })
                .body(PaymentStatusBatchResponse.class);
        if (response == null || response.payments() == null) {
            return Map.of();
        }

        return response.payments().stream()
                .filter(payment -> payment != null && orderIds.contains(payment.orderId()))
                .map(payment -> new PaymentStatusSnapshot(
                        payment.paymentId(), payment.orderId(), payment.status(), payment.updatedAt()))
                .collect(Collectors.toUnmodifiableMap(
                        PaymentStatusSnapshot::orderId,
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private Map<UUID, PaymentStatusSnapshot> handleFallback(Throwable throwable) {
        if (throwable instanceof PaymentReconciliationClientException clientException) {
            throw clientException;
        }
        if (throwable instanceof PaymentReconciliationUnavailableException unavailableException) {
            throw unavailableException;
        }
        throw new PaymentReconciliationUnavailableException(
                "Payment status reconciliation API is currently unavailable.", throwable);
    }

    private record PaymentStatusBatchRequest(List<UUID> orderIds) {
    }

    private record PaymentStatusBatchResponse(List<PaymentStatusResponse> payments) {
    }

    private record PaymentStatusResponse(
            UUID paymentId,
            UUID orderId,
            PaymentReconciliationStatus status,
            Instant updatedAt
    ) {
    }
}
