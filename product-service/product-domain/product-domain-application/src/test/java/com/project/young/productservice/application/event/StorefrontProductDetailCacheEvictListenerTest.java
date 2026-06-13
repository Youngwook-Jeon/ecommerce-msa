package com.project.young.productservice.application.event;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.event.ProductCatalogChangeType;
import com.project.young.productservice.application.dto.event.StorefrontProductDetailCacheEvictRequestedEvent;
import com.project.young.productservice.application.port.output.StorefrontProductDetailCachePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StorefrontProductDetailCacheEvictListenerTest {

    @Mock
    private StorefrontProductDetailCachePort storefrontProductDetailCachePort;

    @InjectMocks
    private StorefrontProductDetailCacheEvictListener listener;

    @Test
    @DisplayName("post-commit evict 이벤트를 받으면 productId로 캐시를 삭제한다")
    void onStorefrontProductDetailCacheEvictRequested_evictsCacheByProductId() {
        UUID productId = UUID.randomUUID();
        StorefrontProductDetailCacheEvictRequestedEvent event =
                new StorefrontProductDetailCacheEvictRequestedEvent(
                        productId,
                        ProductCatalogChangeType.PRODUCT_UPDATED
                );

        listener.onStorefrontProductDetailCacheEvictRequested(event);

        ArgumentCaptor<ProductId> productIdCaptor = ArgumentCaptor.forClass(ProductId.class);
        verify(storefrontProductDetailCachePort).evict(productIdCaptor.capture());
        assertThat(productIdCaptor.getValue().getValue()).isEqualTo(productId);
    }

    @Test
    @DisplayName("Redis evict 실패 시 예외를 전파하지 않는다")
    void onStorefrontProductDetailCacheEvictRequested_whenEvictFails_doesNotPropagate() {
        UUID productId = UUID.randomUUID();
        StorefrontProductDetailCacheEvictRequestedEvent event =
                new StorefrontProductDetailCacheEvictRequestedEvent(
                        productId,
                        ProductCatalogChangeType.IMAGE_CHANGED
                );
        doThrow(new RuntimeException("redis down"))
                .when(storefrontProductDetailCachePort)
                .evict(new ProductId(productId));

        assertThatCode(() -> listener.onStorefrontProductDetailCacheEvictRequested(event))
                .doesNotThrowAnyException();

        verify(storefrontProductDetailCachePort).evict(new ProductId(productId));
    }
}
