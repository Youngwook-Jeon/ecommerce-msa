package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.application.dto.OrderPaymentReconciliationOperationAuditView;

import java.util.List;
import java.util.UUID;

public interface OrderPaymentReconciliationOperationAuditPort {

    void record(OrderPaymentReconciliationOperationAuditView audit);

    List<OrderPaymentReconciliationOperationAuditView> findByOrderId(UUID orderId, int limit);
}
