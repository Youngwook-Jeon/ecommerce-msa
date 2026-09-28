package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.dto.event.PaymentCompletedEvent;
import com.project.young.paymentservice.application.dto.event.PaymentFailedEvent;
import com.project.young.paymentservice.application.dto.event.PaymentOutboxEventType;
import com.project.young.paymentservice.application.dto.event.CustomerRefundCompletedEvent;
import com.project.young.paymentservice.application.dto.event.CustomerRefundFailedEvent;
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

    @Override
    public void enqueueCustomerRefundCompleted(CustomerRefundCompletedEvent event) {
        paymentOutboxJpaRepository.save(PaymentOutboxEntity.builder()
                .eventId(event.eventId()).refundId(event.refundId()).paymentId(event.paymentId())
                .orderId(event.orderId()).userId(event.userId())
                .eventType(PaymentOutboxEventType.CUSTOMER_REFUND_COMPLETED.name())
                .resultVersion(event.resultVersion()).refundCompletedAt(event.refundCompletedAt())
                .amount(java.math.BigDecimal.ZERO).occurredAt(event.occurredAt()).build());
    }

    @Override
    public boolean enqueueCustomerRefundFailed(CustomerRefundFailedEvent event) {
        return paymentOutboxJpaRepository.insertCustomerRefundFailed(event.eventId(), event.refundId(),
                event.paymentId(), event.orderId(), event.userId(), event.failureReason(), event.occurredAt(),
                event.resultVersion(), event.failedAfterCompletion(), event.refundCompletedAt(), event.refundFailedAt()) == 1;
    }
}
