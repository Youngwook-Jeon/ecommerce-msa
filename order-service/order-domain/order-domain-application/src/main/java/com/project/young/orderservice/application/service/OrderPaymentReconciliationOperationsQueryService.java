package com.project.young.orderservice.application.service;

import com.project.young.orderservice.application.dto.OrderPaymentReconciliationEscalationView;
import com.project.young.orderservice.application.port.output.OrderPaymentReconciliationFailurePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderPaymentReconciliationOperationsQueryService {

    private final OrderPaymentReconciliationFailurePort failures;

    public OrderPaymentReconciliationOperationsQueryService(OrderPaymentReconciliationFailurePort failures) {
        this.failures = failures;
    }

    @Transactional(readOnly = true)
    public List<OrderPaymentReconciliationEscalationView> getEscalated(int requestedLimit) {
        return failures.findEscalated(Math.clamp(requestedLimit <= 0 ? 100 : requestedLimit, 1, 500));
    }
}
