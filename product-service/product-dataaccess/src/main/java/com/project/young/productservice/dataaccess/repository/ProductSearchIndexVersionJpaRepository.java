package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductSearchIndexVersionEntity;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductSearchIndexVersionJpaRepository extends JpaRepository<ProductSearchIndexVersionEntity, UUID> {
    boolean existsByStatus(ProductSearchIndexStatus status);
}
