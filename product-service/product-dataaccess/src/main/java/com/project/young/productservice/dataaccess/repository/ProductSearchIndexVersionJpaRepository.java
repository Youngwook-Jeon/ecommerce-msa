package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductSearchIndexVersionEntity;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface ProductSearchIndexVersionJpaRepository extends JpaRepository<ProductSearchIndexVersionEntity, UUID> {
    Optional<ProductSearchIndexVersionEntity> findByStatus(ProductSearchIndexStatus status);

    boolean existsByStatus(ProductSearchIndexStatus status);
}
