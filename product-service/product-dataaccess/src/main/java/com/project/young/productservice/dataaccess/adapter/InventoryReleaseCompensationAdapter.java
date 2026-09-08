package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.port.output.InventoryReleaseCompensationPort;
import com.project.young.productservice.dataaccess.repository.InventoryReleaseCompensationJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class InventoryReleaseCompensationAdapter implements InventoryReleaseCompensationPort {

    private final InventoryReleaseCompensationJpaRepository repository;

    public InventoryReleaseCompensationAdapter(InventoryReleaseCompensationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isProcessed(UUID compensationEventId) {
        return repository.existsById(compensationEventId);
    }

    @Override
    @Transactional
    public boolean recordProcessed(UUID compensationEventId, UUID orderId) {
        return repository.insertIfAbsent(compensationEventId, orderId, Instant.now()) == 1;
    }
}
