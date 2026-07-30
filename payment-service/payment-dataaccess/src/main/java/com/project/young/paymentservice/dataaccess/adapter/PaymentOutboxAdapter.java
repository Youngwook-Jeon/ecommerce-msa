package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.dto.event.PaymentCompletedEvent;
import com.project.young.paymentservice.application.dto.event.PaymentFailedEvent;
import com.project.young.paymentservice.application.dto.event.PaymentOutboxEventType;
import com.project.young.paymentservice.application.port.output.PaymentOutboxPort;
import com.project.young.paymentservice.dataaccess.entity.PaymentOutboxEntity;
import com.project.young.paymentservice.dataaccess.repository.PaymentOutboxJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class PaymentOutboxAdapter implements PaymentOutboxPort {

    private final PaymentOutboxJpaRepository paymentOutboxJpaRepository;

    public PaymentOutboxAdapter(PaymentOutboxJpaRepository paymentOutboxJpaRepository) {
        this.paymentOutboxJpaRepository = paymentOutboxJpaRepository;
    }

    @Override
    public void enqueueCompleted(PaymentCompletedEvent event) {
        paymentOutboxJpaRepository.save(PaymentOutboxEntity.builder()
                .eventId(event.eventId())
                .paymentId(event.paymentId())
                .orderId(event.orderId())
                .userId(event.userId())
                .eventType(PaymentOutboxEventType.PAYMENT_COMPLETED.name())
                .amount(event.amount().getAmount())
                .currency(event.currency())
                .occurredAt(event.occurredAt())
                .build());
    }

    @Override
    public void enqueueFailed(PaymentFailedEvent event) {
        paymentOutboxJpaRepository.save(PaymentOutboxEntity.builder()
                .eventId(event.eventId())
                .paymentId(event.paymentId())
                .orderId(event.orderId())
                .userId(event.userId())
                .eventType(PaymentOutboxEventType.PAYMENT_FAILED.name())
                .amount(event.amount().getAmount())
                .failureReason(event.failureReason())
                .occurredAt(event.occurredAt())
                .build());
    }
}
