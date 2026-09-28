package com.project.young.orderservice.dataaccess.repository;

import com.project.young.orderservice.dataaccess.entity.CustomerRefundEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.project.young.orderservice.domain.valueobject.CustomerRefundStatus;

import java.time.Instant;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRefundJpaRepository extends JpaRepository<CustomerRefundEntity, UUID> {
    Optional<CustomerRefundEntity> findByRefundIdAndUserId(UUID refundId, String userId);

    Optional<CustomerRefundEntity> findByOrderId(UUID orderId);

    @Modifying
    @Query("UPDATE CustomerRefundEntity c SET c.status = :status, c.failureReason = :reason, "
            + "c.updatedAt = :updatedAt WHERE c.refundId = :refundId AND c.status = :requested")
    int updateIfRequested(@Param("refundId") UUID refundId, @Param("requested") CustomerRefundStatus requested,
                          @Param("status") CustomerRefundStatus status, @Param("reason") String reason,
                          @Param("updatedAt") Instant updatedAt);
}
