package com.project.young.orderservice.dataaccess.repository;

import com.project.young.orderservice.domain.entity.CustomerRefund;

import com.project.young.orderservice.dataaccess.entity.CustomerRefundEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.project.young.orderservice.domain.valueobject.CustomerRefundStatus;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRefundJpaRepository extends JpaRepository<CustomerRefundEntity, UUID> {
    Optional<CustomerRefundEntity> findByRefundIdAndUserId(UUID refundId, String userId);

    Optional<CustomerRefundEntity> findByOrderId(UUID orderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CustomerRefundEntity c SET c.status = :#{#refund.status}, c.failureReason = :#{#refund.failureReason}, "
            + "c.resultVersion = :#{#refund.resultVersion}, c.completedAt = :#{#refund.completedAt}, "
            + "c.failedAt = :#{#refund.failedAt}, c.updatedAt = :#{#refund.updatedAt} "
            + "WHERE c.refundId = :#{#refund.id.value} AND c.resultVersion = :expectedVersion AND c.status = :expectedStatus")
    int updateResultIfVersion(@Param("refund") CustomerRefund refund,
                              @Param("expectedVersion") long expectedVersion,
                              @Param("expectedStatus") CustomerRefundStatus expectedStatus);

}
