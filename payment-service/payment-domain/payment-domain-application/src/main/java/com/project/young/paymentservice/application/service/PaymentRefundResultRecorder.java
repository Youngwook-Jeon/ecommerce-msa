package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.RefundCustomerPaymentCommand;
import com.project.young.paymentservice.application.dto.command.RefundPaymentCommand;
import com.project.young.paymentservice.application.dto.event.CustomerRefundCompletedEvent;
import com.project.young.paymentservice.application.dto.event.CustomerRefundFailedEvent;
import com.project.young.paymentservice.application.port.output.CustomerRefundProcessingPort;
import com.project.young.paymentservice.application.port.output.IdGenerator;
import com.project.young.paymentservice.application.port.output.PaymentOutboxPort;
import com.project.young.paymentservice.application.port.output.RefundCompensationPort;
import com.project.young.paymentservice.application.port.output.CustomerRefundObservationPort;
import com.project.young.paymentservice.application.port.output.CustomerRefundReviewPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundResult;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Persists a PSP-confirmed refund and its outbox result in a short DB transaction. */
@Service
public class PaymentRefundResultRecorder {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundResultRecorder.class);

    private final RefundCompensationPort refundCompensationPort;
    private final CustomerRefundProcessingPort customerRefundProcessingPort;
    private final PaymentOutboxPort paymentOutboxPort;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final CustomerRefundObservationPort observations;
    private final CustomerRefundReviewPort reviews;

    public PaymentRefundResultRecorder(RefundCompensationPort refundCompensationPort,
                                       CustomerRefundProcessingPort customerRefundProcessingPort,
                                       PaymentOutboxPort paymentOutboxPort,
                                       IdGenerator idGenerator,
                                       Clock clock,
                                       CustomerRefundObservationPort observations,
                                       CustomerRefundReviewPort reviews) {
        this.refundCompensationPort = refundCompensationPort;
        this.customerRefundProcessingPort = customerRefundProcessingPort;
        this.paymentOutboxPort = paymentOutboxPort;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.observations = observations;
        this.reviews = reviews;
    }

    /** Claim result, successful history, correction outbox and operational review commit together. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordCustomerObservation(RefundCustomerPaymentCommand command, RefundResult result, String reason) {
        var observation = observations.record(command.paymentId(), command.refundId(), result, clock.instant());
        if (observation.isEmpty()) {
            log.debug("Ignored stale customer refund observation refundId={} providerRefundId={} state={}",
                    command.refundId(), result.providerRefundId(), result.state());
            return false;
        }
        if (result.state() == RefundState.PENDING) {
            log.info("Customer refund pending refundId={} providerRefundId={}", command.refundId(), result.providerRefundId());
            return false;
        }
        var observed = observation.get();
        if (result.state() == RefundState.SUCCEEDED) {
            boolean recorded = customerRefundProcessingPort.recordProcessed(command.refundId(), command.paymentId(),
                    command.orderId(), command.userId());
            if (recorded) {
                paymentOutboxPort.enqueueCustomerRefundCompleted(new CustomerRefundCompletedEvent(
                        idGenerator.generateId(), command.refundId(), command.paymentId(), command.orderId(),
                        command.userId(), clock.instant(), 1, observed.succeededAt()));
            }
            log.info("Recorded customer refund success refundId={} providerRefundId={} newlyRecorded={}",
                    command.refundId(), result.providerRefundId(), recorded);
            return recorded;
        }
        boolean afterCompletion = observed.succeededAt() != null;
        String failureReason = reason == null || reason.isBlank() ? "PSP refund failed" : reason;
        if (failureReason.length() > 500) {
            failureReason = failureReason.substring(0, 500);
        }
        boolean recorded = paymentOutboxPort.enqueueCustomerRefundFailed(new CustomerRefundFailedEvent(
                idGenerator.generateId(), command.refundId(), command.paymentId(), command.orderId(),
                command.userId(), failureReason, clock.instant(), 2, afterCompletion,
                observed.succeededAt(), observed.failedAt()));
        if (afterCompletion) {
            reviews.recordConfirmedLateFailure(command.refundId(), command.paymentId(),
                    result.providerRefundId(), failureReason);
        }
        log.warn("Recorded customer refund failure refundId={} providerRefundId={} afterCompletion={} newlyRecorded={}",
                command.refundId(), result.providerRefundId(), afterCompletion, recorded);
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordCompensation(RefundPaymentCommand command, UUID orderId) {
        boolean recorded = refundCompensationPort.recordProcessed(
                command.compensationEventId(), command.paymentId(), orderId);
        log.info("Recorded compensation refund paymentId={} eventId={} newlyRecorded={}",
                command.paymentId(), command.compensationEventId(), recorded);
        return recorded;
    }

}
