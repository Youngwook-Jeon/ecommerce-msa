package com.project.young.paymentservice.web.controller;

import com.project.young.paymentservice.application.compensation.OrderCreatedDltStatus;
import com.project.young.paymentservice.application.dto.query.OrderCreatedDltOperationsView;
import com.project.young.paymentservice.application.service.OrderCreatedDltManualOperationService;
import com.project.young.paymentservice.application.service.OrderCreatedDltOperationsQueryService;
import com.project.young.paymentservice.web.dto.ManualOrderCreatedDltResolutionRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/operations/order-created-dlts")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminOrderCreatedDltOperationsController {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderCreatedDltOperationsController.class);

    private final OrderCreatedDltOperationsQueryService queryService;
    private final OrderCreatedDltManualOperationService manualOperationService;

    public AdminOrderCreatedDltOperationsController(
            OrderCreatedDltOperationsQueryService queryService,
            OrderCreatedDltManualOperationService manualOperationService
    ) {
        this.queryService = queryService;
        this.manualOperationService = manualOperationService;
    }

    @GetMapping
    public ResponseEntity<List<OrderCreatedDltOperationsView>> getItems(
            @RequestParam(required = false) OrderCreatedDltStatus status,
            @RequestParam(defaultValue = "100") int limit
    ) {
        List<OrderCreatedDltOperationsView> items = queryService.getItems(status, limit);
        log.info("Admin order.created DLT lookup status={} count={}", status, items.size());
        return ResponseEntity.ok(items);
    }

    @PostMapping("/{eventId}/replay")
    public ResponseEntity<Void> replay(@PathVariable UUID eventId) {
        manualOperationService.replay(eventId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/resolve")
    public ResponseEntity<Void> resolve(
            @PathVariable UUID eventId,
            @Valid @RequestBody ManualOrderCreatedDltResolutionRequest request
    ) {
        manualOperationService.resolve(eventId, request.reason());
        return ResponseEntity.noContent().build();
    }
}
