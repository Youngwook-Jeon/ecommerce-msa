package com.project.young.productservice.application.service;

import com.project.young.productservice.application.config.InventoryReservationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(InventoryReservationProperties.class)
public class InventoryReservationExpireScheduler {

    private final InventoryReservationApplicationService inventoryReservationApplicationService;
    private final InventoryReservationProperties properties;

    public InventoryReservationExpireScheduler(
            InventoryReservationApplicationService inventoryReservationApplicationService,
            InventoryReservationProperties properties
    ) {
        this.inventoryReservationApplicationService = inventoryReservationApplicationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${product-service.inventory.expire-fixed-delay-ms:30000}")
    public void expireDueReservations() {
        inventoryReservationApplicationService.expireDueReservations(properties.getExpireBatchSize());
    }
}
