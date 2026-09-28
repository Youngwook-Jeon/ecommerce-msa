package com.project.young.paymentservice.application.port.output;

import com.project.young.paymentservice.application.dto.event.PaymentCompletedEvent;
import com.project.young.paymentservice.application.dto.event.PaymentFailedEvent;
import com.project.young.paymentservice.application.dto.event.CustomerRefundCompletedEvent;
import com.project.young.paymentservice.application.dto.event.CustomerRefundFailedEvent;

public interface PaymentOutboxPort {

    void enqueueCompleted(PaymentCompletedEvent event);

    void enqueueFailed(PaymentFailedEvent event);

    void enqueueCustomerRefundCompleted(CustomerRefundCompletedEvent event);

    boolean enqueueCustomerRefundFailed(CustomerRefundFailedEvent event);
}
