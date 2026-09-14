package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.port.output.PaymentReconciliationClientException;
import com.project.young.orderservice.application.port.output.PaymentReconciliationUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestPaymentStatusQueryAdapterTest {

    private MockRestServiceServer server;
    private RestPaymentStatusQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClient = RestClient.builder().baseUrl("http://payment-service");
        server = MockRestServiceServer.bindTo(restClient).build();
        adapter = new RestPaymentStatusQueryAdapter(restClient.build(), circuitBreakerFactory());
    }

    @Test
    void findByOrderIds_postsOneBatchAndMapsPaymentStatusByOrderId() {
        UUID completedOrderId = UUID.randomUUID();
        UUID pendingOrderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        server.expect(requestTo("http://payment-service/internal/orders/payment-statuses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"orderIds\":[\"" + completedOrderId + "\",\"" + pendingOrderId + "\"]}"))
                .andRespond(withSuccess("""
                        {"payments":[{
                          "paymentId":"%s", "orderId":"%s", "status":"COMPLETED",
                          "updatedAt":"2026-09-14T00:00:00Z"
                        }]}
                        """.formatted(paymentId, completedOrderId), MediaType.APPLICATION_JSON));

        Map<UUID, com.project.young.orderservice.application.dto.PaymentStatusSnapshot> result =
                adapter.findByOrderIds(List.of(completedOrderId, pendingOrderId));

        server.verify();
        assertThat(result).containsOnlyKeys(completedOrderId);
        assertThat(result.get(completedOrderId).status())
                .isEqualTo(com.project.young.common.application.contract.payment.PaymentReconciliationStatus.COMPLETED);
    }

    @Test
    void findByOrderIds_doesNotCallPaymentForEmptyInput() {
        assertThat(adapter.findByOrderIds(List.of())).isEmpty();
        server.verify();
    }

    @Test
    void findByOrderIds_treatsPayment4xxAsClientErrorWithoutFallback() {
        server.expect(requestTo("http://payment-service/internal/orders/payment-statuses"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> adapter.findByOrderIds(List.of(UUID.randomUUID())))
                .isInstanceOf(PaymentReconciliationClientException.class);
    }

    @Test
    void findByOrderIds_treatsPayment5xxAsUnavailable() {
        server.expect(requestTo("http://payment-service/internal/orders/payment-statuses"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.findByOrderIds(List.of(UUID.randomUUID())))
                .isInstanceOf(PaymentReconciliationUnavailableException.class);
    }

    private static CircuitBreakerFactory<?, ?> circuitBreakerFactory() {
        Set<Class<? extends Throwable>> ignored = Set.of(PaymentReconciliationClientException.class);
        CircuitBreakerFactory<?, ?> factory = mock(CircuitBreakerFactory.class);
        CircuitBreaker circuitBreaker = mock(CircuitBreaker.class);
        when(factory.create(anyString())).thenReturn(circuitBreaker);
        when(circuitBreaker.run(any(), any())).thenAnswer(invocation -> {
            Supplier<?> toRun = invocation.getArgument(0);
            Function<Throwable, ?> fallback = invocation.getArgument(1);
            try {
                return toRun.get();
            } catch (Throwable throwable) {
                if (ignored.stream().anyMatch(type -> type.isInstance(throwable))) {
                    throw throwable;
                }
                return fallback.apply(throwable);
            }
        });
        return factory;
    }
}
