package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.event.ProductCatalogChangeType;
import com.project.young.productservice.application.dto.event.StorefrontProductDetailCacheEvictRequestedEvent;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.application.port.output.ProductCatalogOutboxPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorefrontProductCatalogInvalidationServiceTest {

    @Mock
    private ProductCatalogOutboxPort productCatalogOutboxPort;

    @Mock
    private IdGenerator idGenerator;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private StorefrontProductCatalogInvalidationService storefrontProductCatalogInvalidationService;

    @Test
    @DisplayName("invalidate는 outbox enqueue 후 post-commit Redis evict 이벤트를 발행한다")
    void invalidate_enqueuesOutboxAndPublishesEvictEvent() {
        UUID productId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(idGenerator.generateId()).thenReturn(eventId);

        storefrontProductCatalogInvalidationService.invalidate(
                new ProductId(productId),
                4L,
                ProductCatalogChangeType.PRODUCT_UPDATED
        );

        verify(productCatalogOutboxPort).enqueue(any());

        ArgumentCaptor<StorefrontProductDetailCacheEvictRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(StorefrontProductDetailCacheEvictRequestedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().productId()).isEqualTo(productId);
        assertThat(eventCaptor.getValue().changeType()).isEqualTo(ProductCatalogChangeType.PRODUCT_UPDATED);
    }
}
