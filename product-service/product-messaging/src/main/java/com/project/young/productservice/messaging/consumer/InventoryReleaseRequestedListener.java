package com.project.young.productservice.messaging.consumer;

import com.project.young.kafka.saga.dto.InventoryReleaseRequestedMessage;
import com.project.young.productservice.application.service.InventoryReservationApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "product-service.saga-events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InventoryReleaseRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryReleaseRequestedListener.class);
    private final InventoryReservationApplicationService inventoryReservationApplicationService;

    public InventoryReleaseRequestedListener(InventoryReservationApplicationService service) {
        this.inventoryReservationApplicationService = service;
    }

    @KafkaListener(topics = "${product-service.saga-events.inventory-release-requested-topic}", groupId = "${product-service.saga-events.inventory-release-requested-consumer-group}", containerFactory = "inventoryReleaseRequestedKafkaListenerContainerFactory")
    public void onInventoryReleaseRequested(InventoryReleaseRequestedMessage message) {
        if (message == null || message.compensationEventId() == null || message.orderId() == null) {
            log.warn("Skipping inventory.release.requested message with missing compensationEventId or orderId"); return;
        }
        log.info("Processing inventory release compensation eventId={} orderId={}", message.compensationEventId(), message.orderId());
        boolean newlyProcessed = inventoryReservationApplicationService.releaseForCompensation(
                message.compensationEventId(), message.orderId());
        log.info("Processed inventory release compensation eventId={} orderId={} newlyProcessed={}",
                message.compensationEventId(), message.orderId(), newlyProcessed);
    }
}
