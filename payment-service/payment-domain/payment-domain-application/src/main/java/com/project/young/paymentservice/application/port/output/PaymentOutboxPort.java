package com.project.young.paymentservice.application.port.output;

import com.project.young.paymentservice.application.dto.event.PaymentCompletedEvent;
import com.project.young.paymentservice.application.dto.event.PaymentFailedEvent;
import com.project.young.paymentservice.application.dto.event.CustomerRefundCompletedEvent;

public interface PaymentOutboxPort {

    void enqueueCompleted(PaymentCompletedEvent event);

    void enqueueFailed(PaymentFailedEvent event);

    void enqueueCustomerRefundCompleted(CustomerRefundCompletedEvent event);
}
