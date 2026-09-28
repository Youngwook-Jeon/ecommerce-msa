package com.project.young.orderservice.application.dto.command;

import java.util.Objects;

/** Kafka location, not a possibly invalid business identifier, is the deduplication key. */
public record RecordCustomerRefundDltCommand(
        String dltTopic, int dltPartition, long dltOffset, String messageKey, String payload,
        String sourceTopic, Integer sourcePartition, Long sourceOffset,
        String exceptionClass, String exceptionMessage
) {
    public RecordCustomerRefundDltCommand {
        Objects.requireNonNull(dltTopic, "dltTopic must not be null");
        if (dltTopic.isBlank() || dltPartition < 0 || dltOffset < 0) {
            throw new IllegalArgumentException("Invalid DLT location");
        }
    }
}
