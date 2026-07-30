package com.project.young.paymentservice.dataaccess.repository;

import com.project.young.paymentservice.dataaccess.entity.PaymentEntity;
import com.project.young.paymentservice.dataaccess.enums.PaymentStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByOrderId(UUID orderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PaymentEntity p
               set p.status = :targetStatus,
                   p.failureReason = :failureReason,
                   p.updatedAt = :updatedAt
             where p.id = :paymentId
               and p.status = :expectedStatus
            """)
    int updateStatusIfCurrent(
            @Param("paymentId") UUID paymentId,
            @Param("expectedStatus") PaymentStatusEntity expectedStatus,
            @Param("targetStatus") PaymentStatusEntity targetStatus,
            @Param("failureReason") String failureReason,
            @Param("updatedAt") Instant updatedAt
    );
}
