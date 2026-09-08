package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.dto.command.RecordInventoryReleaseCompensationDltCommand;
import com.project.young.productservice.application.port.output.InventoryReleaseCompensationDltPort;
import com.project.young.productservice.dataaccess.repository.InventoryReleaseCompensationDltJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
@Transactional
public class InventoryReleaseCompensationDltAdapter implements InventoryReleaseCompensationDltPort {

    private final InventoryReleaseCompensationDltJpaRepository repository;

    public InventoryReleaseCompensationDltAdapter(InventoryReleaseCompensationDltJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean recordIfAbsent(RecordInventoryReleaseCompensationDltCommand command) {
        return repository.insertIfAbsent(
                command.compensationEventId(), command.orderId(), command.sourceTopic(), command.dltTopic(),
                command.sourcePartition(), command.sourceOffset(), truncate(command.failureExceptionClass(), 1024),
                truncate(command.failureMessage(), 4096), Instant.now()) == 1;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
