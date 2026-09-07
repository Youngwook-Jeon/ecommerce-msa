package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.application.dto.event.RefundRequestedEvent;

import java.util.UUID;

public interface RefundRequestedOutboxPort {

    void enqueue(RefundRequestedEvent event);

    boolean existsByCompensationEventId(UUID compensationEventId);
}
