package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.dto.event.ProductCatalogChangedEvent;
import com.project.young.productservice.application.port.output.ProductCatalogOutboxPort;
import com.project.young.productservice.dataaccess.entity.ProductCatalogOutboxEntity;
import com.project.young.productservice.dataaccess.repository.ProductCatalogOutboxJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class ProductCatalogOutboxAdapter implements ProductCatalogOutboxPort {

    private final ProductCatalogOutboxJpaRepository productCatalogOutboxJpaRepository;

    public ProductCatalogOutboxAdapter(ProductCatalogOutboxJpaRepository productCatalogOutboxJpaRepository) {
        this.productCatalogOutboxJpaRepository = productCatalogOutboxJpaRepository;
    }

    @Override
    public void enqueue(ProductCatalogChangedEvent event) {
        productCatalogOutboxJpaRepository.save(ProductCatalogOutboxEntity.builder()
                .eventId(event.eventId())
                .productId(event.productId())
                .categoryId(event.categoryId())
                .changeType(event.changeType().name())
                .occurredAt(event.occurredAt())
                .build());
    }
}
