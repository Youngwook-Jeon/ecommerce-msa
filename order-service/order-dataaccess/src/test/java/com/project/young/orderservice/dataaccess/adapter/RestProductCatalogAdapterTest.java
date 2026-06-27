package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.port.output.CartCatalogLineKey;
import com.project.young.orderservice.application.port.output.ProductCatalogClientException;
import com.project.young.orderservice.application.port.output.ProductCatalogUnavailableException;
import com.project.young.orderservice.application.port.output.view.CartCatalogLineView;
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

import java.math.BigDecimal;
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

class RestProductCatalogAdapterTest {

    private MockRestServiceServer server;
    private RestProductCatalogAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClient = RestClient.builder().baseUrl("http://product-service");
        server = MockRestServiceServer.bindTo(restClient).build();
        adapter = new RestProductCatalogAdapter(restClient.build(), circuitBreakerFactory());
    }

    /**
     * A CircuitBreakerFactory that mirrors the production resilience4j wiring: exceptions listed in
     * {@code ignore-exceptions} are rethrown as-is (not counted, no fallback), everything else goes
     * through the fallback. Keeps {@link RestProductCatalogAdapter} ignore behaviour under test
     * without booting a real resilience4j instance.
     */
    private static CircuitBreakerFactory<?, ?> circuitBreakerFactory() {
        Set<Class<? extends Throwable>> ignoredExceptions = Set.of(ProductCatalogClientException.class);
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
    @DisplayName("resolveLines: product-service 응답을 variant id 기준 맵으로 변환한다")
    void resolveLines_mapsResponseByVariantId() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID optionGroupId = UUID.randomUUID();
        UUID optionValueId = UUID.randomUUID();

        String responseJson = """
                {
                  "lines": [{
                    "productId": "%s",
                    "productVariantId": "%s",
                    "productName": "Phone",
                    "brand": "Brand",
                    "sku": "SKU-1",
                    "imageUrl": "https://img",
                    "unitPrice": 100.00,
                    "purchasable": true,
                    "stockQuantity": 5,
                    "variantOptions": [{
                      "stepOrder": 1,
                      "productOptionGroupId": "%s",
                      "optionGroupName": "Color",
                      "productOptionValueId": "%s",
                      "optionValueName": "Black"
                    }]
                  }]
                }
                """.formatted(productId, variantId, optionGroupId, optionValueId);

        server.expect(requestTo("http://product-service/public/catalog/cart-lines/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"productVariantIds":["%s"]}
                        """.formatted(variantId)))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        Map<UUID, CartCatalogLineView> resolved = adapter.resolveLines(List.of(
                new CartCatalogLineKey(productId, variantId)
        ));

        server.verify();
        assertThat(resolved).containsOnlyKeys(variantId);
        CartCatalogLineView line = resolved.get(variantId);
        assertThat(line.productName()).isEqualTo("Phone");
        assertThat(line.unitPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(line.variantOptions()).hasSize(1);
        assertThat(line.variantOptions().getFirst().optionGroupName()).isEqualTo("Color");
    }

    @Test
    @DisplayName("resolveLines: productId 불일치 라인은 결과에서 제외한다")
    void resolveLines_omitsProductIdMismatch() {
        UUID requestedProductId = UUID.randomUUID();
        UUID responseProductId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        String responseJson = """
                {
                  "lines": [{
                    "productId": "%s",
                    "productVariantId": "%s",
                    "productName": "Phone",
                    "brand": "Brand",
                    "sku": "SKU-1",
                    "imageUrl": null,
                    "unitPrice": 100.00,
                    "purchasable": true,
                    "stockQuantity": 5,
                    "variantOptions": []
                  }]
                }
                """.formatted(responseProductId, variantId);

        server.expect(requestTo("http://product-service/public/catalog/cart-lines/search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        Map<UUID, CartCatalogLineView> resolved = adapter.resolveLines(List.of(
                new CartCatalogLineKey(requestedProductId, variantId)
        ));

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("resolveLines: 빈 입력은 HTTP 호출 없이 빈 맵을 반환한다")
    void resolveLines_emptyInput() {
        assertThat(adapter.resolveLines(List.of())).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("resolveLines: 5xx 서버 오류는 장애로 보고 ProductCatalogUnavailableException(fallback)")
    void resolveLines_serverError_throwsUnavailable() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        server.expect(requestTo("http://product-service/public/catalog/cart-lines/search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.resolveLines(List.of(
                new CartCatalogLineKey(productId, variantId)
        )))
                .isInstanceOf(ProductCatalogUnavailableException.class)
                .hasCauseInstanceOf(Throwable.class);
    }

    @Test
    @DisplayName("resolveLines: 4xx 클라이언트 오류는 장애가 아닌 ProductCatalogClientException으로 그대로 전파")
    void resolveLines_clientError_throwsClientExceptionWithoutFallback() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        server.expect(requestTo("http://product-service/public/catalog/cart-lines/search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> adapter.resolveLines(List.of(
                new CartCatalogLineKey(productId, variantId)
        )))
                .isInstanceOf(ProductCatalogClientException.class);
    }

    @Test
    @DisplayName("resolveLines: 429 rate limit은 장애로 보고 ProductCatalogUnavailableException(fallback)")
    void resolveLines_tooManyRequests_throwsUnavailable() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        server.expect(requestTo("http://product-service/public/catalog/cart-lines/search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> adapter.resolveLines(List.of(
                new CartCatalogLineKey(productId, variantId)
        )))
                .isInstanceOf(ProductCatalogUnavailableException.class)
                .hasCauseInstanceOf(Throwable.class);
    }

    @Test
    @DisplayName("resolveLines: 50개 초과 variant는 예외")
    void resolveLines_tooManyVariants() {
        List<CartCatalogLineKey> keys = java.util.stream.IntStream.range(0, 51)
                .mapToObj(i -> new CartCatalogLineKey(UUID.randomUUID(), UUID.randomUUID()))
                .toList();

        assertThatThrownBy(() -> adapter.resolveLines(keys))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
    }
}
