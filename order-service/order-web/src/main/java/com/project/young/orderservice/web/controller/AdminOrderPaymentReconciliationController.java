package com.project.young.orderservice.web.controller;

import com.project.young.orderservice.application.dto.OrderPaymentReconciliationEscalationView;
import com.project.young.orderservice.application.dto.ManualOrderPaymentReconciliationOperationCommand;
import com.project.young.orderservice.application.dto.OrderPaymentReconciliationOperationAuditView;
import com.project.young.orderservice.application.service.OrderPaymentReconciliationOperationsQueryService;
import com.project.young.orderservice.application.service.OrderPaymentReconciliationOperationsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/operations/payment-reconciliation-escalations")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminOrderPaymentReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderPaymentReconciliationController.class);

    private final OrderPaymentReconciliationOperationsQueryService queryService;
    private final OrderPaymentReconciliationOperationsService operationsService;

    public AdminOrderPaymentReconciliationController(
            OrderPaymentReconciliationOperationsQueryService queryService,
            OrderPaymentReconciliationOperationsService operationsService
    ) {
        this.queryService = queryService;
        this.operationsService = operationsService;
    }

    @GetMapping
    public ResponseEntity<List<OrderPaymentReconciliationEscalationView>> getEscalated(
            @RequestParam(defaultValue = "100") int limit
    ) {
        List<OrderPaymentReconciliationEscalationView> escalated = queryService.getEscalated(limit);
        log.info("Admin payment reconciliation escalation lookup count={}", escalated.size());
        return ResponseEntity.ok(escalated);
    }

    @PostMapping("/{orderId}/replay")
    public ResponseEntity<Void> replay(
            @PathVariable UUID orderId,
            @RequestHeader(name = "X-Request-Id", required = false) UUID requestId,
            Authentication authentication
    ) {
        operationsService.replay(command(orderId, authentication, requestId, null));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{orderId}/close")
    public ResponseEntity<Void> close(
            @PathVariable UUID orderId,
            @RequestHeader(name = "X-Request-Id", required = false) UUID requestId,
            Authentication authentication,
            @Valid @RequestBody ReasonRequest request
    ) {
        operationsService.close(command(orderId, authentication, requestId, request.reason()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{orderId}/refund")
    public ResponseEntity<Map<String, UUID>> requestRefund(
            @PathVariable UUID orderId,
            @RequestHeader(name = "X-Request-Id", required = false) UUID requestId,
            Authentication authentication,
            @Valid @RequestBody ReasonRequest request
    ) {
        UUID compensationEventId = operationsService.requestRefund(command(orderId, authentication, requestId, request.reason()));
        return ResponseEntity.accepted().body(Map.of("compensationEventId", compensationEventId));
    }

    @GetMapping("/{orderId}/history")
    public ResponseEntity<List<OrderPaymentReconciliationOperationAuditView>> getHistory(
            @PathVariable UUID orderId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(queryService.getHistory(orderId, limit));
    }

    private static ManualOrderPaymentReconciliationOperationCommand command(
            UUID orderId,
            Authentication authentication,
            UUID requestId,
            String reason
    ) {
        return new ManualOrderPaymentReconciliationOperationCommand(orderId, authentication.getName(), requestId, reason);
    }

    private record ReasonRequest(@NotBlank @Size(max = 512) String reason) {
    }
}
