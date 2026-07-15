package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.port.output.InventoryReservationClientException;
import com.project.young.orderservice.application.port.output.InventoryReservationConflictException;
import com.project.young.orderservice.application.port.output.InventoryReservationUnavailableException;
import com.project.young.orderservice.application.port.output.view.ReserveInventoryLineResultView;
import com.project.young.orderservice.application.port.output.view.ReserveInventoryLineView;
import com.project.young.orderservice.application.port.output.view.ReserveInventoryResultView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

class RestInventoryReservationAdapterTest {

  private static final UUID CHECKOUT_ID = UUID.fromString("018f0000-0000-7000-8000-000000000301");
  private static final UUID VARIANT_ID = UUID.fromString("018f0000-0000-7000-8000-000000000302");
  private static final UUID RESERVATION_ID = UUID.fromString("018f0000-0000-7000-8000-000000000303");

  private MockRestServiceServer server;
  private RestInventoryReservationAdapter adapter;

  @BeforeEach
  void setUp() {
    RestClient.Builder restClient = RestClient.builder().baseUrl("http://product-service");
    server = MockRestServiceServer.bindTo(restClient).build();
    adapter = new RestInventoryReservationAdapter(restClient.build(), circuitBreakerFactory());
  }

  private static CircuitBreakerFactory<?, ?> circuitBreakerFactory() {
    Set<Class<? extends Throwable>> ignoredExceptions = Set.of(
        InventoryReservationClientException.class,
        InventoryReservationConflictException.class
    );
    CircuitBreakerFactory<?, ?> factory = mock(CircuitBreakerFactory.class);
    CircuitBreaker circuitBreaker = mock(CircuitBreaker.class);
    when(factory.create(anyString())).thenReturn(circuitBreaker);
    when(circuitBreaker.run(any(), any())).thenAnswer(invocation -> {
      Supplier<?> toRun = invocation.getArgument(0);
      Function<Throwable, ?> fallback = invocation.getArgument(1);
      try {
        return toRun.get();
      } catch (Throwable throwable) {
        if (ignoredExceptions.stream().anyMatch(ignored -> ignored.isInstance(throwable))) {
          throw throwable;
        }
        return fallback.apply(throwable);
      }
    });
    return factory;
  }

  @Test
  @DisplayName("reserve: product-service 응답을 ReserveInventoryResultView로 변환한다")
  void reserve_mapsResponse() {
    String responseJson = """
        {
          "checkoutId": "%s",
          "expiresAt": "2026-07-15T10:15:00Z",
          "reusedExisting": false,
          "lines": [{
            "reservationId": "%s",
            "productVariantId": "%s",
            "quantity": 2,
            "status": "ACTIVE"
          }]
        }
        """.formatted(CHECKOUT_ID, RESERVATION_ID, VARIANT_ID);

    server.expect(requestTo("http://product-service/internal/inventory/reservations"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""
            {
              "checkoutId": "%s",
              "lines": [{"productVariantId": "%s", "quantity": 2}]
            }
            """.formatted(CHECKOUT_ID, VARIANT_ID)))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

    ReserveInventoryResultView result = adapter.reserve(
        CHECKOUT_ID,
        List.of(new ReserveInventoryLineView(VARIANT_ID, 2))
    );

    server.verify();
    assertThat(result.checkoutId()).isEqualTo(CHECKOUT_ID);
    assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-07-15T10:15:00Z"));
    assertThat(result.reusedExisting()).isFalse();
    assertThat(result.lines()).containsExactly(
        new ReserveInventoryLineResultView(RESERVATION_ID, VARIANT_ID, 2, "ACTIVE")
    );
  }

  @Test
  @DisplayName("reserve: 빈 lines면 HTTP 호출 없이 예외")
  void reserve_emptyLines() {
    assertThatThrownBy(() -> adapter.reserve(CHECKOUT_ID, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    server.verify();
  }

  @Test
  @DisplayName("reserve: 409는 InventoryReservationConflictException")
  void reserve_conflict() {
    server.expect(requestTo("http://product-service/internal/inventory/reservations"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.CONFLICT));

    assertThatThrownBy(() -> adapter.reserve(
        CHECKOUT_ID,
        List.of(new ReserveInventoryLineView(VARIANT_ID, 1))
    )).isInstanceOf(InventoryReservationConflictException.class);
  }

  @Test
  @DisplayName("reserve: 400은 InventoryReservationClientException")
  void reserve_clientError() {
    server.expect(requestTo("http://product-service/internal/inventory/reservations"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> adapter.reserve(
        CHECKOUT_ID,
        List.of(new ReserveInventoryLineView(VARIANT_ID, 1))
    )).isInstanceOf(InventoryReservationClientException.class);
  }

  @Test
  @DisplayName("reserve: 5xx는 InventoryReservationUnavailableException")
  void reserve_serverError() {
    server.expect(requestTo("http://product-service/internal/inventory/reservations"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError());

    assertThatThrownBy(() -> adapter.reserve(
        CHECKOUT_ID,
        List.of(new ReserveInventoryLineView(VARIANT_ID, 1))
    ))
        .isInstanceOf(InventoryReservationUnavailableException.class)
        .hasCauseInstanceOf(Throwable.class);
  }

  @Test
  @DisplayName("confirm: 204면 정상 종료")
  void confirm_success() {
    server.expect(requestTo(
            "http://product-service/internal/inventory/reservations/%s/confirm".formatted(CHECKOUT_ID)))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));

    adapter.confirm(CHECKOUT_ID);
    server.verify();
  }

  @Test
  @DisplayName("confirm: 404는 InventoryReservationClientException")
  void confirm_notFound() {
    server.expect(requestTo(
            "http://product-service/internal/inventory/reservations/%s/confirm".formatted(CHECKOUT_ID)))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> adapter.confirm(CHECKOUT_ID))
        .isInstanceOf(InventoryReservationClientException.class);
  }

  @Test
  @DisplayName("release: 204면 정상 종료")
  void release_success() {
    server.expect(requestTo(
            "http://product-service/internal/inventory/reservations/%s/release".formatted(CHECKOUT_ID)))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));

    adapter.release(CHECKOUT_ID);
    server.verify();
  }
}
