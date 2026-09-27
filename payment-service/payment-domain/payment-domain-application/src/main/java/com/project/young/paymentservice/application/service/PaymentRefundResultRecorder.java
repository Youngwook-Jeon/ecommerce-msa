package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.RefundCustomerPaymentCommand;
import com.project.young.paymentservice.application.dto.command.RefundPaymentCommand;
import com.project.young.paymentservice.application.dto.event.CustomerRefundCompletedEvent;
import com.project.young.paymentservice.application.port.output.CustomerRefundProcessingPort;
import com.project.young.paymentservice.application.port.output.IdGenerator;
import com.project.young.paymentservice.application.port.output.PaymentOutboxPort;
import com.project.young.paymentservice.application.port.output.RefundCompensationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Persists a PSP-accepted refund and its outbox result in a short DB transaction. */
@Service
public class PaymentRefundResultRecorder {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundResultRecorder.class);

    private final RefundCompensationPort refundCompensationPort;
    private final CustomerRefundProcessingPort customerRefundProcessingPort;
    private final PaymentOutboxPort paymentOutboxPort;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public PaymentRefundResultRecorder(RefundCompensationPort refundCompensationPort,
                                       CustomerRefundProcessingPort customerRefundProcessingPort,
                                       PaymentOutboxPort paymentOutboxPort,
                                       IdGenerator idGenerator,
                                       Clock clock) {
        this.refundCompensationPort = refundCompensationPort;
        this.customerRefundProcessingPort = customerRefundProcessingPort;
        this.paymentOutboxPort = paymentOutboxPort;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordCompensation(RefundPaymentCommand command, UUID orderId) {
        boolean recorded = refundCompensationPort.recordProcessed(
                command.compensationEventId(), command.paymentId(), orderId);
        log.info("Recorded compensation refund paymentId={} eventId={} newlyRecorded={}",
                command.paymentId(), command.compensationEventId(), recorded);
        return recorded;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordCustomerRefund(RefundCustomerPaymentCommand command) {
        boolean recorded = customerRefundProcessingPort.recordProcessed(
                command.refundId(), command.paymentId(), command.orderId(), command.userId());
        if (!recorded) {
            log.debug("Customer refund result already recorded refundId={} paymentId={}",
                    command.refundId(), command.paymentId());
            return false;
        }
        paymentOutboxPort.enqueueCustomerRefundCompleted(new CustomerRefundCompletedEvent(
                idGenerator.generateId(), command.refundId(), command.paymentId(), command.orderId(),
                command.userId(), clock.instant()));
        log.info("Recorded customer refund and completion event refundId={} paymentId={}",
                command.refundId(), command.paymentId());
        return true;
    }
}
