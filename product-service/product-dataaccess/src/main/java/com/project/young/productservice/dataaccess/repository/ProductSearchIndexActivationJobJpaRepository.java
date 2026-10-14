package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexActivationStatus;
import com.project.young.productservice.dataaccess.entity.ProductSearchIndexActivationJobEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ProductSearchIndexActivationJobJpaRepository extends JpaRepository<ProductSearchIndexActivationJobEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM ProductSearchIndexActivationJobEntity j WHERE j.id = :id")
    Optional<ProductSearchIndexActivationJobEntity> findLockedById(@Param("id") UUID id);

    Optional<ProductSearchIndexActivationJobEntity> findByRequestedByAndIdempotencyKey(String requestedBy, String idempotencyKey);

    boolean existsByStatusIn(Collection<ProductSearchIndexActivationStatus> statuses);
}
