package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductSearchIndexBuildJobEntity;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

import java.util.UUID;

public interface ProductSearchIndexBuildJobJpaRepository extends JpaRepository<ProductSearchIndexBuildJobEntity, UUID> {
    Optional<ProductSearchIndexBuildJobEntity> findByRequestedByAndIdempotencyKey(String requestedBy, String idempotencyKey);

    boolean existsByStatusIn(Collection<ProductSearchIndexBuildStatus> statuses);
}
