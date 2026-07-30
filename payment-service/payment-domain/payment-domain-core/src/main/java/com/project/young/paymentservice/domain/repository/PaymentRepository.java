package com.project.young.paymentservice.domain.repository;

import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentStatus;

import java.util.Optional;

public interface PaymentRepository {

    void insert(Payment payment);

    /**
     * Atomically changes status only when the persisted status still equals {@code expectedStatus}.
     */
    boolean updateStatus(Payment payment, PaymentStatus expectedStatus);

    Optional<Payment> findById(PaymentId paymentId);

    Optional<Payment> findByOrderId(OrderId orderId);
}
