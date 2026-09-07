package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.dto.event.RefundRequestedEvent;
import com.project.young.orderservice.application.port.output.RefundRequestedOutboxPort;
import com.project.young.orderservice.dataaccess.entity.RefundRequestedOutboxEntity;
import com.project.young.orderservice.dataaccess.repository.RefundRequestedOutboxJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@Transactional
public class RefundRequestedOutboxAdapter implements RefundRequestedOutboxPort {

    private final RefundRequestedOutboxJpaRepository repository;

    public RefundRequestedOutboxAdapter(RefundRequestedOutboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void enqueue(RefundRequestedEvent event) {
        repository.save(RefundRequestedOutboxEntity.builder()
                .compensationEventId(event.compensationEventId())
                .paymentId(event.paymentId())
                .orderId(event.orderId())
                .userId(event.userId())
                .reason(event.reason())
                .occurredAt(event.occurredAt())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCompensationEventId(UUID compensationEventId) {
        return repository.existsByCompensationEventId(compensationEventId);
    }
}
