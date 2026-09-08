package com.project.young.productservice.application.dto.command;

import java.util.UUID;

public record RecordInventoryReleaseCompensationDltCommand(
        UUID compensationEventId,
        UUID orderId,
        String sourceTopic,
        String dltTopic,
        Integer sourcePartition,
        Long sourceOffset,
        String failureExceptionClass,
        String failureMessage
) {
}
