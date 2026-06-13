package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.event.ProductCatalogChangedEvent;
import com.project.young.productservice.application.dto.event.ProductCatalogChangeType;
import com.project.young.productservice.application.dto.event.StorefrontProductDetailCacheEvictRequestedEvent;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.application.port.output.ProductCatalogOutboxPort;
import com.project.young.productservice.domain.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Enqueues durable catalog-change events in the DB transaction and schedules Redis eviction after commit.
 */
@Service
@Slf4j
public class StorefrontProductCatalogInvalidationService {

    private final ProductCatalogOutboxPort productCatalogOutboxPort;
    private final IdGenerator idGenerator;
    private final ApplicationEventPublisher applicationEventPublisher;

    public StorefrontProductCatalogInvalidationService(
            ProductCatalogOutboxPort productCatalogOutboxPort,
            IdGenerator idGenerator,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.productCatalogOutboxPort = productCatalogOutboxPort;
        this.idGenerator = idGenerator;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void invalidate(Product product, ProductCatalogChangeType changeType) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("product must not be null");
        }
        Long categoryId = product.getCategoryId().map(CategoryId::getValue).orElse(null);
        invalidate(product.getId(), categoryId, changeType);
    }

    public void invalidate(ProductId productId, Long categoryId, ProductCatalogChangeType changeType) {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        if (changeType == null) {
            throw new IllegalArgumentException("changeType must not be null");
        }

        productCatalogOutboxPort.enqueue(new ProductCatalogChangedEvent(
                idGenerator.generateId(),
                productId.getValue(),
                categoryId,
                changeType,
                Instant.now()
        ));
        applicationEventPublisher.publishEvent(
                new StorefrontProductDetailCacheEvictRequestedEvent(productId.getValue(), changeType)
        );
        log.debug("Scheduled storefront catalog invalidation for product {} ({})", productId.getValue(), changeType);
    }

    public void invalidate(UUID productIdValue, Long categoryId, ProductCatalogChangeType changeType) {
        invalidate(new ProductId(productIdValue), categoryId, changeType);
    }
}
