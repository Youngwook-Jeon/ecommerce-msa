package com.project.young.paymentservice.application.port.output;

import com.project.young.paymentservice.application.dto.event.PaymentCompletedEvent;
import com.project.young.paymentservice.application.dto.event.PaymentFailedEvent;

public interface PaymentOutboxPort {

    void enqueueCompleted(PaymentCompletedEvent event);

    void enqueueFailed(PaymentFailedEvent event);
}
