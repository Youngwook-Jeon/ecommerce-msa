package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.ProcessPaymentCommand;
import com.project.young.paymentservice.application.dto.event.PaymentCompletedEvent;
import com.project.young.paymentservice.application.dto.event.PaymentFailedEvent;
import com.project.young.paymentservice.application.port.output.IdGenerator;
import com.project.young.paymentservice.application.port.output.PaymentGatewayPort;
import com.project.young.paymentservice.application.port.output.PaymentGatewayResult;
import com.project.young.paymentservice.application.port.output.PaymentOutboxPort;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.exception.PaymentStateConflictException;
import com.project.young.paymentservice.domain.repository.PaymentRepository;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentStatus;
import com.project.young.paymentservice.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Processes payments for the order-payment saga.
 * <p>
 * Persists the payment aggregate, runs the (stub) gateway, and enqueues outbox events
 * in a single local transaction so Debezium can relay {@code payment.completed} /
 * {@code payment.failed}.
 */
@Service
public class PaymentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentOutboxPort paymentOutboxPort;
    private final PaymentGatewayPort paymentGatewayPort;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public PaymentApplicationService(
            PaymentRepository paymentRepository,
            PaymentOutboxPort paymentOutboxPort,
            PaymentGatewayPort paymentGatewayPort,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentOutboxPort = paymentOutboxPort;
        this.paymentGatewayPort = paymentGatewayPort;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public Payment processPayment(ProcessPaymentCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        OrderId orderId = new OrderId(command.orderId());
        Optional<Payment> existing = paymentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (payment.getStatus().isTerminal()) {
                log.debug(
                        "Payment for order {} already terminal (status={}); skipping",
                        orderId.getValue(),
                        payment.getStatus()
                );
                return payment;
            }
            log.info("Resuming pending payment {} for order {}", payment.getId().getValue(), orderId.getValue());
            return finalizePayment(payment);
        }

        PaymentId paymentId = new PaymentId(idGenerator.generateId());
        Payment payment = Payment.createPending(
                paymentId,
                orderId,
                new UserId(command.userId()),
                command.amount(),
                command.currency()
        );
        paymentRepository.insert(payment);
        log.info("Created pending payment {} for order {}", paymentId.getValue(), orderId.getValue());
        return finalizePayment(payment);
    }

    private Payment finalizePayment(Payment payment) {
        PaymentGatewayResult gatewayResult = paymentGatewayPort.process(payment);
        Instant occurredAt = clock.instant();

        if (gatewayResult.success()) {
            payment.complete();
            assertStatusUpdated(payment, PaymentStatus.PENDING);
            paymentOutboxPort.enqueueCompleted(new PaymentCompletedEvent(
                    idGenerator.generateId(),
                    payment.getId().getValue(),
                    payment.getOrderId().getValue(),
                    payment.getUserId().value(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    occurredAt
            ));
            log.info("Payment {} completed for order {}", payment.getId().getValue(), payment.getOrderId().getValue());
            return payment;
        }

        payment.fail(gatewayResult.failureReason());
        assertStatusUpdated(payment, PaymentStatus.PENDING);
        paymentOutboxPort.enqueueFailed(new PaymentFailedEvent(
                idGenerator.generateId(),
                payment.getId().getValue(),
                payment.getOrderId().getValue(),
                payment.getUserId().value(),
                payment.getAmount(),
                gatewayResult.failureReason(),
                occurredAt
        ));
        log.info(
                "Payment {} failed for order {}: {}",
                payment.getId().getValue(),
                payment.getOrderId().getValue(),
                gatewayResult.failureReason()
        );
        return payment;
    }

    private void assertStatusUpdated(Payment payment, PaymentStatus expectedStatus) {
        if (!paymentRepository.updateStatus(payment, expectedStatus)) {
            throw new PaymentStateConflictException(
                    "Concurrent payment status update for payment " + payment.getId().getValue()
                            + " (expected " + expectedStatus + ", target " + payment.getStatus() + ").");
        }
    }
}
