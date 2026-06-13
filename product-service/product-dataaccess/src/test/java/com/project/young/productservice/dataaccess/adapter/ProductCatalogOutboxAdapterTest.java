package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.dto.event.ProductCatalogChangedEvent;
import com.project.young.productservice.application.dto.event.ProductCatalogChangeType;
import com.project.young.productservice.dataaccess.entity.ProductCatalogOutboxEntity;
import com.project.young.productservice.dataaccess.repository.ProductCatalogOutboxJpaRepository;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductCatalogOutboxAdapterTest {

    @Mock
    private ProductCatalogOutboxJpaRepository productCatalogOutboxJpaRepository;

    @InjectMocks
    private ProductCatalogOutboxAdapter productCatalogOutboxAdapter;

    @Test
    @DisplayName("enqueue: outbox 이벤트 필드를 entity에 매핑해 저장한다")
    void enqueue_mapsEventFieldsAndSaves() {
        UUID eventId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-06-13T08:03:10.343300Z");
        ProductCatalogChangedEvent event = new ProductCatalogChangedEvent(
                eventId,
                productId,
                4L,
                ProductCatalogChangeType.PRODUCT_UPDATED,
                occurredAt
        );

        productCatalogOutboxAdapter.enqueue(event);

        ArgumentCaptor<ProductCatalogOutboxEntity> captor = ArgumentCaptor.forClass(ProductCatalogOutboxEntity.class);
        verify(productCatalogOutboxJpaRepository).save(captor.capture());

        ProductCatalogOutboxEntity saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getProductId()).isEqualTo(productId);
        assertThat(saved.getCategoryId()).isEqualTo(4L);
        assertThat(saved.getChangeType()).isEqualTo("PRODUCT_UPDATED");
        assertThat(saved.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(saved.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("enqueue: categoryId가 null이면 entity에도 null로 저장한다")
    void enqueue_whenCategoryIdNull_savesNullCategoryId() {
        UUID eventId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ProductCatalogChangedEvent event = new ProductCatalogChangedEvent(
                eventId,
                productId,
                null,
                ProductCatalogChangeType.DELETED,
                Instant.now()
        );

        productCatalogOutboxAdapter.enqueue(event);

        ArgumentCaptor<ProductCatalogOutboxEntity> captor = ArgumentCaptor.forClass(ProductCatalogOutboxEntity.class);
        verify(productCatalogOutboxJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getCategoryId()).isNull();
        assertThat(captor.getValue().getChangeType()).isEqualTo("DELETED");
    }
}
