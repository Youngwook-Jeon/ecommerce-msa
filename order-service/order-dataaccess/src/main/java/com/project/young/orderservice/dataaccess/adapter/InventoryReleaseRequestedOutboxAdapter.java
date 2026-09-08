package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.dto.event.InventoryReleaseRequestedEvent;
import com.project.young.orderservice.application.port.output.InventoryReleaseRequestedOutboxPort;
import com.project.young.orderservice.dataaccess.entity.InventoryReleaseRequestedOutboxEntity;
import com.project.young.orderservice.dataaccess.repository.InventoryReleaseRequestedOutboxJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@Transactional
public class InventoryReleaseRequestedOutboxAdapter implements InventoryReleaseRequestedOutboxPort {

    private final InventoryReleaseRequestedOutboxJpaRepository repository;

    public InventoryReleaseRequestedOutboxAdapter(InventoryReleaseRequestedOutboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void enqueue(InventoryReleaseRequestedEvent event) {
        repository.save(InventoryReleaseRequestedOutboxEntity.builder()
                .compensationEventId(event.compensationEventId())
                .orderId(event.orderId())
                .reason(event.reason())
                .occurredAt(event.occurredAt())
                .build());
    }

    @Override
    public boolean existsByCompensationEventId(UUID compensationEventId) {
        return repository.existsByCompensationEventId(compensationEventId);
    }
}
