package com.project.young.paymentservice.domain.repository;

import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    void insert(Payment payment);

    /**
     * Atomically changes status only when the persisted status still equals {@code expectedStatus}.
     */
    boolean updateStatus(Payment payment, PaymentStatus expectedStatus);

    /**
     * Persists provider session fields ({@code provider}, {@code provider_payment_id}, {@code client_secret})
     * for an existing PENDING payment.
     */
    void updateProviderSession(Payment payment);

    Optional<Payment> findById(PaymentId paymentId);

    Optional<Payment> findByOrderId(OrderId orderId);

    Optional<Payment> findByProviderPaymentId(String provider, String providerPaymentId);

    List<Payment> findPendingWithProviderSessionUpdatedBefore(Instant threshold, int limit);
}
