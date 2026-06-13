package com.project.young.productservice.messaging.consumer;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.port.output.StorefrontProductDetailCachePort;
import com.project.young.productservice.messaging.dto.ProductCatalogChangedMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductCatalogCacheInvalidationListenerTest {

    @Mock
    private StorefrontProductDetailCachePort storefrontProductDetailCachePort;

    @InjectMocks
    private ProductCatalogCacheInvalidationListener listener;

    @Test
    @DisplayName("Debezium catalog 메시지를 받으면 productId로 캐시를 삭제한다")
    void onProductCatalogChanged_evictsCacheByProductId() {
        UUID productId = UUID.randomUUID();
        ProductCatalogChangedMessage message = sampleMessage(productId);

        listener.onProductCatalogChanged(message);

        ArgumentCaptor<ProductId> productIdCaptor = ArgumentCaptor.forClass(ProductId.class);
        verify(storefrontProductDetailCachePort).evict(productIdCaptor.capture());
        assertThat(productIdCaptor.getValue().getValue()).isEqualTo(productId);
    }

    @Test
    @DisplayName("message가 null이면 evict를 건너뛴다")
    void onProductCatalogChanged_whenMessageNull_skipsEvict() {
        listener.onProductCatalogChanged(null);

        verify(storefrontProductDetailCachePort, never()).evict(any());
    }

    @Test
    @DisplayName("productId가 null이면 evict를 건너뛴다")
    void onProductCatalogChanged_whenProductIdNull_skipsEvict() {
        ProductCatalogChangedMessage message = new ProductCatalogChangedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                4L,
                "PRODUCT_UPDATED",
                Instant.parse("2026-06-13T08:03:10.343300Z"),
                null,
                Instant.parse("2026-06-13T08:03:10.345273Z")
        );

        listener.onProductCatalogChanged(message);

        verify(storefrontProductDetailCachePort, never()).evict(any());
    }

    private static ProductCatalogChangedMessage sampleMessage(UUID productId) {
        return new ProductCatalogChangedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                productId,
                4L,
                "PRODUCT_UPDATED",
                Instant.parse("2026-06-13T08:03:10.343300Z"),
                null,
                Instant.parse("2026-06-13T08:03:10.345273Z")
        );
    }
}
