package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.command.RecordInventoryReleaseCompensationDltCommand;
import com.project.young.productservice.application.port.output.InventoryReleaseCompensationDltPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class InventoryReleaseCompensationDltApplicationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReleaseCompensationDltApplicationService.class);

    private final InventoryReleaseCompensationDltPort inventoryReleaseCompensationDltPort;

    public InventoryReleaseCompensationDltApplicationService(
            InventoryReleaseCompensationDltPort inventoryReleaseCompensationDltPort
    ) {
        this.inventoryReleaseCompensationDltPort = inventoryReleaseCompensationDltPort;
    }

    @Transactional
    public boolean recordManualFollowUp(RecordInventoryReleaseCompensationDltCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.compensationEventId(), "compensationEventId must not be null");
        Objects.requireNonNull(command.orderId(), "orderId must not be null");

        boolean newlyCreated = inventoryReleaseCompensationDltPort.recordIfAbsent(command);
        log.warn(
                "Recorded inventory-release DLT manual follow-up eventId={} orderId={} newlyCreated={} cause={}",
                command.compensationEventId(), command.orderId(), newlyCreated, command.failureExceptionClass());
        return newlyCreated;
    }
}
