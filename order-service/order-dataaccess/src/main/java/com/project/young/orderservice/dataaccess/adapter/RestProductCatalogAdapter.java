package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.port.output.CartCatalogLineKey;
import com.project.young.orderservice.application.port.output.ProductCatalogClientException;
import com.project.young.orderservice.application.port.output.ProductCatalogPort;
import com.project.young.orderservice.application.port.output.ProductCatalogUnavailableException;
import com.project.young.orderservice.application.port.output.view.CartCatalogLineView;
import com.project.young.orderservice.application.port.output.view.CartCatalogOptionLineView;
import com.project.young.orderservice.dataaccess.adapter.catalog.ProductCatalogLineResponse;
import com.project.young.orderservice.dataaccess.adapter.catalog.ProductCatalogLinesSearchRequest;
import com.project.young.orderservice.dataaccess.adapter.catalog.ProductCatalogLinesSearchResponse;
import com.project.young.orderservice.dataaccess.adapter.catalog.ProductCatalogOptionLineResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RestProductCatalogAdapter implements ProductCatalogPort {

    private static final String CART_LINES_SEARCH_PATH = "/public/catalog/cart-lines/search";
    private static final String CIRCUIT_BREAKER_ID = "productCatalog";
    private static final int MAX_BATCH_SIZE = 50;

    private final RestClient productCatalogRestClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public RestProductCatalogAdapter(
            @Qualifier("productCatalogRestClient") RestClient productCatalogRestClient,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory
    ) {
        this.productCatalogRestClient = productCatalogRestClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    @Override
    public Map<UUID, CartCatalogLineView> resolveLines(Collection<CartCatalogLineKey> lines) {
        if (lines == null || lines.isEmpty()) {
            return Map.of();
        }

        Map<UUID, CartCatalogLineKey> keyByVariantId = lines.stream()
                .collect(Collectors.toMap(
                        CartCatalogLineKey::productVariantId,
                        Function.identity(),
                        (left, right) -> left
                ));

        List<UUID> variantIds = List.copyOf(keyByVariantId.keySet());
        if (variantIds.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Cannot resolve more than " + MAX_BATCH_SIZE + " cart catalog lines at once.");
        }

        CircuitBreaker circuitBreaker = circuitBreakerFactory.create(CIRCUIT_BREAKER_ID);
        return circuitBreaker.run(
                () -> fetchAndMap(variantIds, keyByVariantId),
                this::handleFallback
        );
    }

    private Map<UUID, CartCatalogLineView> fetchAndMap(
            List<UUID> variantIds,
            Map<UUID, CartCatalogLineKey> keyByVariantId
    ) {
        ProductCatalogLinesSearchResponse response = productCatalogRestClient.post()
                .uri(CART_LINES_SEARCH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProductCatalogLinesSearchRequest(variantIds))
                .retrieve()
                // Most 4xx responses mean our request is wrong and must not open the circuit.
                // 429 (rate limited) is a downstream protection signal and counts as a failure.
                // 5xx keeps the default handling (HttpServerErrorException).
                .onStatus(
                        status -> status.is4xxClientError()
                                && status.value() != HttpStatus.TOO_MANY_REQUESTS.value(),
                        (request, clientResponse) -> {
                            throw new ProductCatalogClientException(
                                    "Product catalog rejected the cart-lines request with status "
                                            + clientResponse.getStatusCode() + ".");
                        })
                .body(ProductCatalogLinesSearchResponse.class);

        if (response == null || response.lines().isEmpty()) {
            return Map.of();
        }

        Map<UUID, CartCatalogLineView> resolved = new HashMap<>();
        for (ProductCatalogLineResponse line : response.lines()) {
            CartCatalogLineKey key = keyByVariantId.get(line.productVariantId());
            if (key == null) {
                continue;
            }
            if (!key.productId().equals(line.productId())) {
                continue;
            }
            resolved.put(line.productVariantId(), toView(line));
        }
        return Map.copyOf(resolved);
    }

    private CartCatalogLineView toView(ProductCatalogLineResponse line) {
        return new CartCatalogLineView(
                line.productId(),
                line.productVariantId(),
                line.productName(),
                line.brand(),
                line.sku(),
                line.imageUrl(),
                line.unitPrice(),
                line.purchasable(),
                line.stockQuantity(),
                line.variantOptions().stream().map(this::toOptionView).toList()
        );
    }

    private CartCatalogOptionLineView toOptionView(ProductCatalogOptionLineResponse line) {
        return new CartCatalogOptionLineView(
                line.stepOrder(),
                line.productOptionGroupId(),
                line.optionGroupName(),
                line.productOptionValueId(),
                line.optionValueName()
        );
    }

    private Map<UUID, CartCatalogLineView> handleFallback(Throwable throwable) {
        log.error("Calling ProductCatalog API has failed: {}", throwable.getMessage());
        throw new ProductCatalogUnavailableException("Product catalog is currently unavailable.", throwable);
    }
}
