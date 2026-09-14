package com.project.young.orderservice.web.controller;

import com.project.young.orderservice.application.dto.OrderPaymentReconciliationEscalationView;
import com.project.young.orderservice.application.service.OrderPaymentReconciliationOperationsQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/operations/payment-reconciliation-escalations")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminOrderPaymentReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderPaymentReconciliationController.class);

    private final OrderPaymentReconciliationOperationsQueryService queryService;

    public AdminOrderPaymentReconciliationController(OrderPaymentReconciliationOperationsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<List<OrderPaymentReconciliationEscalationView>> getEscalated(
            @RequestParam(defaultValue = "100") int limit
    ) {
        List<OrderPaymentReconciliationEscalationView> escalated = queryService.getEscalated(limit);
        log.info("Admin payment reconciliation escalation lookup count={}", escalated.size());
        return ResponseEntity.ok(escalated);
    }
}
