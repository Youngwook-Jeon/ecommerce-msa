package com.project.young.paymentservice.application.port.output;

import com.project.young.paymentservice.domain.entity.Payment;

public interface PaymentGatewayPort {

    PaymentGatewayResult process(Payment payment);
}
