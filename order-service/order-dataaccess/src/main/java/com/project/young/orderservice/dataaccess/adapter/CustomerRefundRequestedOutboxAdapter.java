package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.dto.event.CustomerRefundRequestedEvent;
import com.project.young.orderservice.application.port.output.CustomerRefundRequestedOutboxPort;
import com.project.young.orderservice.dataaccess.entity.CustomerRefundRequestedOutboxEntity;
import com.project.young.orderservice.dataaccess.repository.CustomerRefundRequestedOutboxJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class CustomerRefundRequestedOutboxAdapter implements CustomerRefundRequestedOutboxPort {

    private final CustomerRefundRequestedOutboxJpaRepository repository;

    public CustomerRefundRequestedOutboxAdapter(CustomerRefundRequestedOutboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void enqueue(CustomerRefundRequestedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        repository.save(CustomerRefundRequestedOutboxEntity.builder()
                .refundId(event.refundId())
                .paymentId(event.paymentId())
                .orderId(event.orderId())
                .userId(event.userId())
                .reason(event.reason())
                .occurredAt(event.occurredAt())
                .build());
    }
}
