package com.project.young.productservice.web.internal.controller;

import com.project.young.productservice.application.dto.result.ReserveInventoryResult;
import com.project.young.productservice.application.service.InventoryReservationApplicationService;
import com.project.young.productservice.web.internal.dto.ReserveInventoryRequest;
import com.project.young.productservice.web.internal.dto.ReserveInventoryResponse;
import com.project.young.productservice.web.internal.mapper.InventoryReservationWebMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal inventory soft-hold API for checkout/payment orchestration.
 * Gateway path prefix: {@code /api/v1/product_service/internal/inventory}.
 */
@Slf4j
@RestController
@RequestMapping("internal/inventory/reservations")
public class InternalInventoryReservationController {

    private final InventoryReservationApplicationService inventoryReservationApplicationService;
    private final InventoryReservationWebMapper inventoryReservationWebMapper;

    public InternalInventoryReservationController(
            InventoryReservationApplicationService inventoryReservationApplicationService,
            InventoryReservationWebMapper inventoryReservationWebMapper
    ) {
        this.inventoryReservationApplicationService = inventoryReservationApplicationService;
        this.inventoryReservationWebMapper = inventoryReservationWebMapper;
    }

    @PostMapping
    public ResponseEntity<ReserveInventoryResponse> reserve(
            @Valid @RequestBody ReserveInventoryRequest request
    ) {
        log.info(
                "REST request to reserve inventory: checkoutId={}, lineCount={}",
                request.checkoutId(),
                request.lines().size()
        );
        ReserveInventoryResult result = inventoryReservationApplicationService.reserve(
                inventoryReservationWebMapper.toCommand(request)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryReservationWebMapper.toResponse(result));
    }

    @PostMapping("/{checkoutId}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable UUID checkoutId) {
        log.info("REST request to confirm inventory reservation: checkoutId={}", checkoutId);
        inventoryReservationApplicationService.confirm(checkoutId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{checkoutId}/release")
    public ResponseEntity<Void> release(@PathVariable UUID checkoutId) {
        log.info("REST request to release inventory reservation: checkoutId={}", checkoutId);
        inventoryReservationApplicationService.release(checkoutId);
        return ResponseEntity.noContent().build();
    }
}
