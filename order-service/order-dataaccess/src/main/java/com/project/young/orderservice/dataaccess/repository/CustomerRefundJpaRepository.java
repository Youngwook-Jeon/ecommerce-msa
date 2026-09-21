package com.project.young.orderservice.dataaccess.repository;

import com.project.young.orderservice.dataaccess.entity.CustomerRefundEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRefundJpaRepository extends JpaRepository<CustomerRefundEntity, UUID> {
    Optional<CustomerRefundEntity> findByRefundIdAndUserId(UUID refundId, String userId);

    Optional<CustomerRefundEntity> findByOrderId(UUID orderId);
}
