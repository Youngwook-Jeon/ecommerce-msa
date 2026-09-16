package com.project.young.orderservice.application.service;

import com.project.young.orderservice.application.dto.OrderPaymentReconciliationEscalationView;
import com.project.young.orderservice.application.dto.OrderPaymentReconciliationOperationAuditView;
import com.project.young.orderservice.application.port.output.OrderPaymentReconciliationFailurePort;
import com.project.young.orderservice.application.port.output.OrderPaymentReconciliationOperationAuditPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderPaymentReconciliationOperationsQueryService {

    private final OrderPaymentReconciliationFailurePort failures;
    private final OrderPaymentReconciliationOperationAuditPort audits;

    public OrderPaymentReconciliationOperationsQueryService(
            OrderPaymentReconciliationFailurePort failures,
            OrderPaymentReconciliationOperationAuditPort audits
    ) {
        this.failures = failures;
        this.audits = audits;
    }

    @Transactional(readOnly = true)
    public List<OrderPaymentReconciliationEscalationView> getEscalated(int requestedLimit) {
        return failures.findEscalated(Math.clamp(requestedLimit <= 0 ? 100 : requestedLimit, 1, 500));
    }

    @Transactional(readOnly = true)
    public List<OrderPaymentReconciliationOperationAuditView> getHistory(UUID orderId, int requestedLimit) {
        return audits.findByOrderId(orderId, Math.clamp(requestedLimit <= 0 ? 100 : requestedLimit, 1, 500));
    }
}
