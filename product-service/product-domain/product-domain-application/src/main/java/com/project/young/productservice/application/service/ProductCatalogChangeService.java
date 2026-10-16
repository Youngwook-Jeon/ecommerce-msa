package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.event.ProductCatalogChangedEvent;
import com.project.young.productservice.application.dto.event.ProductCatalogChangeType;
import com.project.young.productservice.application.dto.event.StorefrontProductDetailCacheEvictRequestedEvent;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.application.port.output.ProductCatalogOutboxPort;
import com.project.young.productservice.application.port.output.ProductCatalogVersionPort;
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
public class ProductCatalogChangeService {

    private final ProductCatalogOutboxPort productCatalogOutboxPort;
    private final ProductCatalogVersionPort versions;
    private final IdGenerator idGenerator;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ProductCatalogChangeService(
            ProductCatalogOutboxPort productCatalogOutboxPort,
            IdGenerator idGenerator,
            ApplicationEventPublisher applicationEventPublisher,
            ProductCatalogVersionPort versions
    ) {
        this.versions = versions;
        this.productCatalogOutboxPort = productCatalogOutboxPort;
        this.idGenerator = idGenerator;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void verifyVersion(UUID productId, long expectedVersion) {
        versions.verifyVersion(productId, expectedVersion);
    }

    public void recordChange(Product product, ProductCatalogChangeType changeType, long expectedVersion) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("product must not be null");
        }
        Long categoryId = product.getCategoryId().map(CategoryId::getValue).orElse(null);
        recordChange(product.getId(), categoryId, changeType, expectedVersion);
    }

    public void recordChange(ProductId productId, Long categoryId, ProductCatalogChangeType changeType, long expectedVersion) {
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
                Instant.now(),
                expectedVersion
        ));
        applicationEventPublisher.publishEvent(
                new StorefrontProductDetailCacheEvictRequestedEvent(productId.getValue(), changeType)
        );
        log.debug("Recorded catalog change for product {} ({})", productId.getValue(), changeType);
    }

    public void recordChange(UUID productIdValue, Long categoryId, ProductCatalogChangeType changeType, long expectedVersion) {
        recordChange(new ProductId(productIdValue), categoryId, changeType, expectedVersion);
    }
}
