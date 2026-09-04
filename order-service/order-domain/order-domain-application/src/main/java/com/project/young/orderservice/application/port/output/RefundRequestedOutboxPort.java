package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.application.dto.event.RefundRequestedEvent;

public interface RefundRequestedOutboxPort {
    void enqueue(RefundRequestedEvent event);
}
