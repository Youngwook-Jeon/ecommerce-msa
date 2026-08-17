package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.application.dto.event.OrderCreatedEvent;

public interface OrderOutboxPort {

    void enqueueCreated(OrderCreatedEvent event);
}
