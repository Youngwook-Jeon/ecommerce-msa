package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.RecordRefundCompensationDltCommand;
import com.project.young.paymentservice.application.port.output.RefundCompensationDltPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
public class RefundCompensationDltApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RefundCompensationDltApplicationService.class);
    private final RefundCompensationDltPort port;

    public RefundCompensationDltApplicationService(RefundCompensationDltPort port) { this.port = port; }

    @Transactional
    public boolean recordManualFollowUp(RecordRefundCompensationDltCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.compensationEventId(), "compensationEventId must not be null");
        Objects.requireNonNull(command.paymentId(), "paymentId must not be null");
        Objects.requireNonNull(command.orderId(), "orderId must not be null");
        boolean created = port.recordIfAbsent(command);
        log.warn("Recorded refund DLT manual follow-up eventId={} paymentId={} orderId={} newlyCreated={} cause={}",
                command.compensationEventId(), command.paymentId(), command.orderId(), created, command.failureExceptionClass());
        return created;
    }
}
