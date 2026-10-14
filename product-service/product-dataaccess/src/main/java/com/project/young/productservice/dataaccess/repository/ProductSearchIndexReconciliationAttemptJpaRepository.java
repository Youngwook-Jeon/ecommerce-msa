package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.ProductSearchIndexReconciliationAttemptEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductSearchIndexReconciliationAttemptJpaRepository extends JpaRepository<ProductSearchIndexReconciliationAttemptEntity, UUID> {
    Optional<ProductSearchIndexReconciliationAttemptEntity> findFirstByJobIdOrderByCreatedAtDescIdDesc(UUID jobId);

    boolean existsByJobIdAndFinishedAtIsNull(UUID jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ProductSearchIndexReconciliationAttemptEntity a WHERE a.id = :id")
    Optional<ProductSearchIndexReconciliationAttemptEntity> findLockedById(@Param("id") UUID id);
}
