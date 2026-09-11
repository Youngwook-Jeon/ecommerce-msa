package com.project.young.paymentservice.dataaccess.repository;

import com.project.young.paymentservice.dataaccess.entity.PaymentEntity;
import com.project.young.paymentservice.dataaccess.enums.PaymentStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByOrderId(UUID orderId);

    Optional<PaymentEntity> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);

    @Query("""
            select p from PaymentEntity p
             where p.status = :status
               and p.provider is not null
               and p.providerPaymentId is not null
               and p.clientSecret is not null
               and p.updatedAt < :threshold
             order by p.updatedAt asc
            """)
    List<PaymentEntity> findPendingWithProviderSessionUpdatedBefore(
            @Param("status") PaymentStatusEntity status,
            @Param("threshold") Instant threshold,
            Pageable pageable
    );

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PaymentEntity p
               set p.provider = :provider,
                   p.providerPaymentId = :providerPaymentId,
                   p.clientSecret = :clientSecret,
                   p.updatedAt = :updatedAt
             where p.id = :paymentId
            """)
    int updateProviderSession(
            @Param("paymentId") UUID paymentId,
            @Param("provider") String provider,
            @Param("providerPaymentId") String providerPaymentId,
            @Param("clientSecret") String clientSecret,
            @Param("updatedAt") Instant updatedAt
    );
}
