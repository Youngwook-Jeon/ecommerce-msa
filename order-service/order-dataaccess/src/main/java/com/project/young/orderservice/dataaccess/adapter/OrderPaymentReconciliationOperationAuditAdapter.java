package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.dto.OrderPaymentReconciliationOperationAuditView;
import com.project.young.orderservice.application.port.output.OrderPaymentReconciliationOperationAuditPort;
import com.project.young.orderservice.dataaccess.entity.OrderPaymentReconciliationOperationAuditEntity;
import com.project.young.orderservice.dataaccess.repository.OrderPaymentReconciliationOperationAuditJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@Transactional
public class OrderPaymentReconciliationOperationAuditAdapter implements OrderPaymentReconciliationOperationAuditPort {

    private final OrderPaymentReconciliationOperationAuditJpaRepository repository;

    public OrderPaymentReconciliationOperationAuditAdapter(
            OrderPaymentReconciliationOperationAuditJpaRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public void record(OrderPaymentReconciliationOperationAuditView audit) {
        repository.save(OrderPaymentReconciliationOperationAuditEntity.builder()
                .id(audit.id())
                .orderId(audit.orderId())
                .operatorId(audit.operatorId())
                .requestId(audit.requestId())
                .operation(audit.operation())
                .reason(audit.reason())
                .compensationEventId(audit.compensationEventId())
                .occurredAt(audit.occurredAt())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderPaymentReconciliationOperationAuditView> findByOrderId(UUID orderId, int limit) {
        return repository.findByOrderIdOrderByOccurredAtDesc(orderId, PageRequest.of(0, Math.clamp(limit, 1, 500)))
                .stream()
                .map(entity -> new OrderPaymentReconciliationOperationAuditView(
                        entity.getId(), entity.getOrderId(), entity.getOperatorId(), entity.getRequestId(),
                        entity.getOperation(), entity.getReason(), entity.getCompensationEventId(), entity.getOccurredAt()))
                .toList();
    }
}
