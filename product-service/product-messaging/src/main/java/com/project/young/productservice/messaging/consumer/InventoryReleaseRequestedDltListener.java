package com.project.young.productservice.messaging.consumer;

import com.project.young.kafka.saga.dto.InventoryReleaseRequestedMessage;
import com.project.young.productservice.application.dto.command.RecordInventoryReleaseCompensationDltCommand;
import com.project.young.productservice.application.service.InventoryReleaseCompensationDltApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "product-service.saga-events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InventoryReleaseRequestedDltListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryReleaseRequestedDltListener.class);

    private final InventoryReleaseCompensationDltApplicationService dltApplicationService;
    private final String requestedTopic;
    private final String dltTopic;

    public InventoryReleaseRequestedDltListener(
            InventoryReleaseCompensationDltApplicationService dltApplicationService,
            @Value("${product-service.saga-events.inventory-release-requested-topic}") String requestedTopic,
            @Value("${product-service.saga-events.inventory-release-requested-dlt-topic}") String dltTopic
    ) {
        this.dltApplicationService = dltApplicationService;
        this.requestedTopic = requestedTopic;
        this.dltTopic = dltTopic;
    }

    @KafkaListener(
            topics = "${product-service.saga-events.inventory-release-requested-dlt-topic}",
            groupId = "${product-service.saga-events.inventory-release-requested-dlt-consumer-group}",
            containerFactory = "inventoryReleaseRequestedDltKafkaListenerContainerFactory"
    )
    public void onInventoryReleaseRequestedDlt(
            InventoryReleaseRequestedMessage message,
            Acknowledgment acknowledgment,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) String exceptionFqcn,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer originalPartition,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long originalOffset
    ) {
        if (message == null || message.compensationEventId() == null || message.orderId() == null) {
            log.warn("Skipping inventory.release.requested.DLT message with missing compensationEventId/orderId cause={}",
                    exceptionFqcn);
            acknowledgment.acknowledge();
            return;
        }

        boolean newlyCreated = dltApplicationService.recordManualFollowUp(
                new RecordInventoryReleaseCompensationDltCommand(
                        message.compensationEventId(), message.orderId(),
                        originalTopic == null || originalTopic.isBlank() ? requestedTopic : originalTopic,
                        dltTopic, originalPartition, originalOffset, exceptionFqcn, exceptionMessage));
        acknowledgment.acknowledge();

        log.info("Acked inventory.release.requested.DLT eventId={} orderId={} newlyCreated={}",
                message.compensationEventId(), message.orderId(), newlyCreated);
    }
}
