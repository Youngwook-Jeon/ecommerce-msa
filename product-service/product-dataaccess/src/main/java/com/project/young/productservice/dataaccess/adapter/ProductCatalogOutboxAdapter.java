package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.dto.event.ProductCatalogChangedEvent;
import com.project.young.productservice.application.port.output.ProductCatalogOutboxPort;
import com.project.young.productservice.dataaccess.entity.ProductCatalogOutboxEntity;
import com.project.young.productservice.dataaccess.repository.ProductCatalogOutboxJpaRepository;
import org.springframework.stereotype.Repository;
import com.project.young.productservice.dataaccess.catalog.ProductCatalogRevisionStore;
import com.project.young.productservice.application.port.output.ProductCatalogChangeActorPort;
import com.project.young.productservice.application.dto.event.ProductCatalogChangeType;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class ProductCatalogOutboxAdapter implements ProductCatalogOutboxPort {

    private final ProductCatalogOutboxJpaRepository productCatalogOutboxJpaRepository;

    private final ProductCatalogRevisionStore revisions;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final ObjectProvider<ProductCatalogChangeActorPort> actors;

    public ProductCatalogOutboxAdapter(ProductCatalogOutboxJpaRepository productCatalogOutboxJpaRepository, ProductCatalogRevisionStore revisions,
                                     JdbcTemplate jdbc, EntityManager entityManager, ObjectProvider<ProductCatalogChangeActorPort> actors) {
        this.productCatalogOutboxJpaRepository = productCatalogOutboxJpaRepository;
        this.revisions = revisions;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.actors = actors;
    }

    @Override
    public void enqueue(ProductCatalogChangedEvent event) {
        long revision = event.changeType() == ProductCatalogChangeType.PRODUCT_CREATED
                ? revisions.current(event.productId()) : revisions.advance(event.productId(), event.expectedVersion());
        entityManager.flush();
        String actor = actors.getIfAvailable(() -> () -> "SYSTEM").currentActor();
        jdbc.update("""
                INSERT INTO product_catalog_change_history(event_id, product_id, revision, change_type, occurred_at, changed_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """, event.eventId(), event.productId(), revision, event.changeType().name(), java.sql.Timestamp.from(event.occurredAt()), actor);
        productCatalogOutboxJpaRepository.save(ProductCatalogOutboxEntity.builder()
                .eventId(event.eventId())
                .catalogRevision(revision)
                .productId(event.productId())
                .categoryId(event.categoryId())
                .changeType(event.changeType().name())
                .occurredAt(event.occurredAt())
                .build());
    }
}
