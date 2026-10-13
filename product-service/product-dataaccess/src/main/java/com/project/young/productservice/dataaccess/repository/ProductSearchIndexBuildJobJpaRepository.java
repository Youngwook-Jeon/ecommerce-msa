package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductSearchIndexBuildJobEntity;
import com.project.young.productservice.application.searchindex.model.ProductSearchIndexBuildStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.Optional;

import java.util.UUID;

public interface ProductSearchIndexBuildJobJpaRepository extends JpaRepository<ProductSearchIndexBuildJobEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM ProductSearchIndexBuildJobEntity j WHERE j.id = :id")
    Optional<ProductSearchIndexBuildJobEntity> findLockedById(@Param("id") UUID id);

    Optional<ProductSearchIndexBuildJobEntity> findByRequestedByAndIdempotencyKey(String requestedBy, String idempotencyKey);

    boolean existsByStatusIn(Collection<ProductSearchIndexBuildStatus> statuses);
}
