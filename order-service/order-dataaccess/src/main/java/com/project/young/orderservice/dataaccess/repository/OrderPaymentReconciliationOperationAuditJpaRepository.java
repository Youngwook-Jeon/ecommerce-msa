package com.project.young.orderservice.dataaccess.repository;

import com.project.young.orderservice.dataaccess.entity.OrderPaymentReconciliationOperationAuditEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderPaymentReconciliationOperationAuditJpaRepository
        extends JpaRepository<OrderPaymentReconciliationOperationAuditEntity, UUID> {

    List<OrderPaymentReconciliationOperationAuditEntity> findByOrderIdOrderByOccurredAtDesc(UUID orderId, Pageable pageable);
}
