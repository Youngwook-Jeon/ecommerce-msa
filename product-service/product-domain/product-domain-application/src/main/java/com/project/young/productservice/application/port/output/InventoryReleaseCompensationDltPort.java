package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.dto.command.RecordInventoryReleaseCompensationDltCommand;

/**
 * Durable manual-follow-up store for inventory-release records that exhausted Kafka retries.
 */
public interface InventoryReleaseCompensationDltPort {

    boolean recordIfAbsent(RecordInventoryReleaseCompensationDltCommand command);
}
