package com.project.young.paymentservice.application.dto.command;

import java.util.UUID;

public record RecordRefundCompensationDltCommand(
        UUID compensationEventId, UUID paymentId, UUID orderId, String sourceTopic, String dltTopic,
        Integer sourcePartition, Long sourceOffset, String failureExceptionClass, String failureMessage
) { }
