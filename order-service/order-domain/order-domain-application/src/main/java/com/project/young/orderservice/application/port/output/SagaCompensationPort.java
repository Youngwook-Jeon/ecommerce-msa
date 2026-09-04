package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.application.compensation.CompensationDecision;
import com.project.young.orderservice.application.compensation.CompensationHandlingStatus;
import com.project.young.orderservice.application.dto.compensation.RecordManualCompensationCommand;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface SagaCompensationPort {

    Optional<SagaCompensationView> findByEventId(UUID eventId);

    /**
     * Inserts a MANUAL compensation row. Caller must ensure uniqueness on eventId.
     */
    SagaCompensationView insertManual(
            RecordManualCompensationCommand command,
            CompensationDecision decision,
            CompensationHandlingStatus handlingStatus
    );

    List<SagaCompensationView> findByHandlingStatus(CompensationHandlingStatus status, int limit);

    void updateHandlingStatus(UUID eventId, CompensationHandlingStatus status);
}
