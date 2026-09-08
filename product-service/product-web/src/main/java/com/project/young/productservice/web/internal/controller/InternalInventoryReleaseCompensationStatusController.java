package com.project.young.productservice.web.internal.controller;

import com.project.young.productservice.application.service.InventoryReservationApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal reconciliation endpoint; it only reads the durable compensation result.
 */
@RestController
@RequestMapping("/internal/inventory/release-compensations")
public class InternalInventoryReleaseCompensationStatusController {

    private static final Logger log = LoggerFactory.getLogger(InternalInventoryReleaseCompensationStatusController.class);

    private final InventoryReservationApplicationService inventoryReservationApplicationService;

    public InternalInventoryReleaseCompensationStatusController(
            InventoryReservationApplicationService inventoryReservationApplicationService
    ) {
        this.inventoryReservationApplicationService = inventoryReservationApplicationService;
    }

    @GetMapping("/{compensationEventId}")
    public ResponseEntity<Void> getStatus(@PathVariable UUID compensationEventId) {
        boolean processed = inventoryReservationApplicationService
                .isReleaseCompensationProcessed(compensationEventId);
        log.debug("Inventory release compensation reconciliation lookup eventId={} processed={}",
                compensationEventId, processed);
        return processed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
