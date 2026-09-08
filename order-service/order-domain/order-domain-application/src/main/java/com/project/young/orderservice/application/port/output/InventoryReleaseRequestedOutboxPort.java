package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.application.dto.event.InventoryReleaseRequestedEvent;

import java.util.UUID;

public interface InventoryReleaseRequestedOutboxPort {

    void enqueue(InventoryReleaseRequestedEvent event);

    boolean existsByCompensationEventId(UUID compensationEventId);
}
