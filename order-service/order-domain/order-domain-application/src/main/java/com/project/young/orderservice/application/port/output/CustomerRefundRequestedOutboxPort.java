package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.application.dto.event.CustomerRefundRequestedEvent;

public interface CustomerRefundRequestedOutboxPort {

    void enqueue(CustomerRefundRequestedEvent event);
}
