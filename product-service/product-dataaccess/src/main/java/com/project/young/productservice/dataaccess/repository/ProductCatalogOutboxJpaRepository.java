package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductCatalogOutboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductCatalogOutboxJpaRepository extends JpaRepository<ProductCatalogOutboxEntity, UUID> {

    List<ProductCatalogOutboxEntity> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}
