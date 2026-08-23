package com.project.young.orderservice.application.service;

import com.project.young.orderservice.application.compensation.CompensationDecision;
import com.project.young.orderservice.application.compensation.CompensationDecisionClassifier;
import com.project.young.orderservice.application.compensation.CompensationHandlingStatus;
import com.project.young.orderservice.application.dto.compensation.RecordManualCompensationCommand;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;
import com.project.young.orderservice.application.port.output.CompensationObservationPort;
import com.project.young.orderservice.application.port.output.SagaCompensationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * First-slice DLT handling: classify → persist MANUAL → emit observation.
 * Does not execute refund/replay yet; policies are stored for later automation.
 */
@Service
public class SagaCompensationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SagaCompensationApplicationService.class);

    private final SagaCompensationPort sagaCompensationPort;
    private final CompensationObservationPort compensationObservationPort;

    public SagaCompensationApplicationService(
            SagaCompensationPort sagaCompensationPort,
            CompensationObservationPort compensationObservationPort
    ) {
        this.sagaCompensationPort = sagaCompensationPort;
        this.compensationObservationPort = compensationObservationPort;
    }

    @Transactional
    public SagaCompensationView recordManualFromDlt(RecordManualCompensationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.eventId(), "eventId must not be null");
        Objects.requireNonNull(command.orderId(), "orderId must not be null");

        Optional<SagaCompensationView> existing = sagaCompensationPort.findByEventId(command.eventId());
        if (existing.isPresent()) {
            SagaCompensationView duplicate = existing.get();
            log.info(
                    "Skipping duplicate saga compensation for eventId={} orderId={} status={}",
                    duplicate.eventId(),
                    duplicate.orderId(),
                    duplicate.handlingStatus()
            );
            return duplicate;
        }

        CompensationDecision decision = CompensationDecisionClassifier.classify(
                command.failureExceptionClass(),
                command.failureMessage()
        );

        SagaCompensationView saved = sagaCompensationPort.insertManual(
                command,
                decision,
                CompensationHandlingStatus.MANUAL
        );

        log.warn(
                "Recorded MANUAL saga compensation id={} eventId={} orderId={} recommendedAction={} refundSla={} reason={}",
                saved.id(),
                saved.eventId(),
                saved.orderId(),
                saved.recommendedAction(),
                saved.refundSla(),
                saved.classificationReason()
        );

        compensationObservationPort.recordManualCompensation(saved);
        return saved;
    }
}
