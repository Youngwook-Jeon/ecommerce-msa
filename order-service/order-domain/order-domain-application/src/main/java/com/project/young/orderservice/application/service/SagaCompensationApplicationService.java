package com.project.young.orderservice.application.service;

import com.project.young.orderservice.application.compensation.CompensationDecision;
import com.project.young.orderservice.application.compensation.CompensationDecisionClassifier;
import com.project.young.orderservice.application.compensation.CompensationHandlingStatus;
import com.project.young.orderservice.application.dto.compensation.RecordManualCompensationCommand;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;
import com.project.young.orderservice.application.port.output.CompensationObservationPort;
import com.project.young.orderservice.application.port.output.SagaCompensationPort;
import com.project.young.orderservice.application.port.output.RefundRequestedOutboxPort;
import com.project.young.orderservice.application.compensation.CompensationRecommendedAction;
import com.project.young.orderservice.application.dto.event.RefundRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.time.Instant;

/**
 * DLT handling: classify → persist MANUAL → enqueue refund request when needed → emit observation.
 * The refund is not executed here; the transactional outbox is relayed asynchronously.
 */
@Service
public class SagaCompensationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SagaCompensationApplicationService.class);

    private final SagaCompensationPort sagaCompensationPort;
    private final CompensationObservationPort compensationObservationPort;
    private final RefundRequestedOutboxPort refundRequestedOutboxPort;

    public SagaCompensationApplicationService(
            SagaCompensationPort sagaCompensationPort,
            CompensationObservationPort compensationObservationPort,
            RefundRequestedOutboxPort refundRequestedOutboxPort
    ) {
        this.sagaCompensationPort = sagaCompensationPort;
        this.compensationObservationPort = compensationObservationPort;
        this.refundRequestedOutboxPort = refundRequestedOutboxPort;
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

        if (saved.recommendedAction() == CompensationRecommendedAction.REFUND && saved.paymentId() != null) {
            refundRequestedOutboxPort.enqueue(new RefundRequestedEvent(saved.eventId(), saved.paymentId(), saved.orderId(),
                    saved.userId(), saved.classificationReason(), Instant.now()));
            log.info("Enqueued refund.requested outbox event compensationEventId={} paymentId={}", saved.eventId(), saved.paymentId());
        }

        compensationObservationPort.recordManualCompensation(saved);
        return saved;
    }
}
