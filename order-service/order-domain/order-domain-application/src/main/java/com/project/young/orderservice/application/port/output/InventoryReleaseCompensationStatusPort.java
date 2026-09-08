package com.project.young.orderservice.application.port.output;

import java.util.UUID;

/**
 * Reads whether Product Service has durably applied an inventory release compensation.
 */
public interface InventoryReleaseCompensationStatusPort {

    boolean isProcessed(UUID compensationEventId);
}
