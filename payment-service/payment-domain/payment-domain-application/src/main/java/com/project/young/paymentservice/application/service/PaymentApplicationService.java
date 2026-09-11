package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.ApplyProviderPaymentResultCommand;
import com.project.young.paymentservice.application.dto.command.ProcessPaymentCommand;
import com.project.young.paymentservice.application.dto.command.RefundPaymentCommand;
import com.project.young.paymentservice.application.dto.event.PaymentCompletedEvent;
import com.project.young.paymentservice.application.dto.event.PaymentFailedEvent;
import com.project.young.paymentservice.application.dto.query.ClientSecretView;
import com.project.young.paymentservice.application.port.output.IdGenerator;
import com.project.young.paymentservice.application.port.output.PaymentOutboxPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.ProviderPaymentSession;
import com.project.young.paymentservice.application.port.output.ProviderEventIdempotencyPort;
import com.project.young.paymentservice.application.port.output.ProviderSessionRequestPort;
import com.project.young.paymentservice.application.port.output.RefundCompensationPort;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.exception.PaymentClientSecretNotReadyException;
import com.project.young.paymentservice.domain.exception.PaymentDomainException;
import com.project.young.paymentservice.domain.exception.PaymentNotFoundException;
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
import java.util.UUID;

/**
 * Orchestrates payment initiation and provider webhook settlement for the order-payment saga.
 */
@Service
public class PaymentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentOutboxPort paymentOutboxPort;
    private final PaymentProviderPort paymentProviderPort;
    private final ProviderEventIdempotencyPort providerEventIdempotencyPort;
    private final RefundCompensationPort refundCompensationPort;
    private final ProviderSessionRequestPort providerSessionRequestPort;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public PaymentApplicationService(
            PaymentRepository paymentRepository,
            PaymentOutboxPort paymentOutboxPort,
            PaymentProviderPort paymentProviderPort,
            ProviderEventIdempotencyPort providerEventIdempotencyPort,
            RefundCompensationPort refundCompensationPort,
            ProviderSessionRequestPort providerSessionRequestPort,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentOutboxPort = paymentOutboxPort;
        this.paymentProviderPort = paymentProviderPort;
        this.providerEventIdempotencyPort = providerEventIdempotencyPort;
        this.refundCompensationPort = refundCompensationPort;
        this.providerSessionRequestPort = providerSessionRequestPort;
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
            providerSessionRequestPort.enqueue(payment.getId().getValue());
            return payment;
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
        providerSessionRequestPort.enqueue(payment.getId().getValue());
        log.info("Created pending payment {} and provider-session request for order {}", paymentId.getValue(), orderId.getValue());
        return payment;
    }

    @Transactional
    public Payment createProviderSession(UUID paymentIdValue) {
        Payment payment = paymentRepository.findById(new PaymentId(paymentIdValue))
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentIdValue));
        if (payment.getStatus().isTerminal() || payment.hasProviderSession()) return payment;
        return ensureProviderSessionAndMaybeSettle(payment);
    }

    /**
     * Applies an async provider result (Stripe webhook). Idempotent on {@code eventId}.
     *
     * @return true if the payment was newly settled; false if ignored/already processed
     */
    @Transactional
    public boolean applyProviderPaymentResult(ApplyProviderPaymentResultCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Optional<Payment> found = paymentRepository.findByProviderPaymentId(
                command.provider().name(),
                command.providerPaymentId()
        );
        if (found.isEmpty()) {
            log.warn(
                    "No payment found for provider={} providerPaymentId={}; acknowledging event {}",
                    command.provider(),
                    command.providerPaymentId(),
                    command.eventId()
            );
            return false;
        }

        Payment payment = found.get();
        boolean firstTime = providerEventIdempotencyPort.tryMarkProcessed(
                command.eventId(),
                payment.getId().getValue(),
                command.provider().name(),
                command.outcome().providerEventType()
        );
        if (!firstTime) {
            log.debug("Provider event {} already processed; skipping", command.eventId());
            return false;
        }

        if (payment.getStatus().isTerminal()) {
            log.info(
                    "Payment {} already terminal (status={}); event {} recorded only",
                    payment.getId().getValue(),
                    payment.getStatus(),
                    command.eventId()
            );
            return false;
        }

        Instant occurredAt = clock.instant();
        if (command.success()) {
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
            log.info(
                    "Payment {} completed via provider webhook (event={}, providerPaymentId={})",
                    payment.getId().getValue(),
                    command.eventId(),
                    command.providerPaymentId()
            );
            return true;
        }

        if (!command.outcome().isTerminal()) {
            log.info(
                    "Recorded non-terminal provider payment attempt failure paymentId={} eventId={} providerPaymentId={}",
                    payment.getId().getValue(),
                    command.eventId(),
                    command.providerPaymentId()
            );
            return false;
        }

        payment.fail(command.failureReason());
        assertStatusUpdated(payment, PaymentStatus.PENDING);
        paymentOutboxPort.enqueueFailed(new PaymentFailedEvent(
                idGenerator.generateId(),
                payment.getId().getValue(),
                payment.getOrderId().getValue(),
                payment.getUserId().value(),
                payment.getAmount(),
                command.failureReason(),
                occurredAt
        ));
        log.info(
                "Payment {} failed via provider webhook (event={}, reason={})",
                payment.getId().getValue(),
                command.eventId(),
                command.failureReason()
        );
        return true;
    }

    @Transactional(readOnly = true)
    public boolean hasProviderPayment(ApplyProviderPaymentResultCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return paymentRepository.findByProviderPaymentId(
                command.provider().name(),
                command.providerPaymentId()
        ).isPresent();
    }

    @Transactional(readOnly = true)
    public ClientSecretView getClientSecretByOrderId(UUID orderIdValue) {
        Objects.requireNonNull(orderIdValue, "orderId must not be null");
        Payment payment = paymentRepository.findByOrderId(new OrderId(orderIdValue))
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for order " + orderIdValue));

        if (payment.getClientSecret() == null || payment.getClientSecret().isBlank()) {
            throw new PaymentClientSecretNotReadyException(
                    "Client secret is not available yet for order " + orderIdValue
                            + " (payment status=" + payment.getStatus() + ").");
        }

        return new ClientSecretView(
                payment.getId().getValue(),
                payment.getOrderId().getValue(),
                payment.getProvider() == null ? null : payment.getProvider().name(),
                payment.getClientSecret(),
                payment.getStatus().name()
        );
    }

    @Transactional
    public boolean refundPayment(UUID paymentIdValue, UUID compensationEventId) {
        return refundPayment(new RefundPaymentCommand(compensationEventId, paymentIdValue, null));
    }

    /**
     * Applies a saga refund once per compensation event. Provider calls use the same event id as
     * their idempotency key, so a retry after an uncertain provider response is also safe.
     *
     * @return {@code true} when the compensation was newly applied; {@code false} when already processed
     */
    @Transactional
    public boolean refundPayment(RefundPaymentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.compensationEventId(), "compensationEventId must not be null");
        Objects.requireNonNull(command.paymentId(), "paymentId must not be null");

        if (refundCompensationPort.isProcessed(command.compensationEventId())) {
            log.info(
                    "Skipping already processed refund compensation eventId={} paymentId={}",
                    command.compensationEventId(),
                    command.paymentId()
            );
            return false;
        }

        log.info(
                "Refunding completed payment {} for compensation event {}",
                command.paymentId(),
                command.compensationEventId()
        );
        Payment payment = paymentRepository.findById(new PaymentId(command.paymentId()))
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + command.paymentId()));
        if (command.orderId() != null && !payment.getOrderId().getValue().equals(command.orderId())) {
            log.warn(
                    "Rejecting refund compensation eventId={} because paymentId={} belongs to orderId={} not orderId={}",
                    command.compensationEventId(),
                    command.paymentId(),
                    payment.getOrderId().getValue(),
                    command.orderId()
            );
            throw new PaymentDomainException("Payment order does not match refund compensation: " + command.paymentId());
        }
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            log.warn("Rejecting refund for payment {} in status {}", command.paymentId(), payment.getStatus());
            throw new PaymentDomainException("Only completed payments can be refunded: " + command.paymentId());
        }
        paymentProviderPort.refund(payment, command.compensationEventId().toString());
        boolean newlyRecorded = refundCompensationPort.recordProcessed(
                command.compensationEventId(),
                command.paymentId(),
                payment.getOrderId().getValue()
        );
        log.info(
                "Refund provider call completed paymentId={} compensationEventId={} newlyRecorded={}",
                command.paymentId(),
                command.compensationEventId(),
                newlyRecorded
        );
        return newlyRecorded;
    }

    @Transactional(readOnly = true)
    public boolean isRefundCompensationProcessed(UUID compensationEventId) {
        Objects.requireNonNull(compensationEventId, "compensationEventId must not be null");
        return refundCompensationPort.isProcessed(compensationEventId);
    }

    private Payment ensureProviderSessionAndMaybeSettle(Payment payment) {
        if (payment.hasProviderSession()) {
            log.info(
                    "Payment {} already has provider session (provider={}, providerPaymentId={}); awaiting confirmation",
                    payment.getId().getValue(),
                    payment.getProvider(),
                    payment.getProviderPaymentId()
            );
            return payment;
        }

        ProviderPaymentSession session = paymentProviderPort.createPayment(payment);
        payment.assignProviderSession(
                session.provider(),
                session.providerPaymentId(),
                session.clientSecret()
        );
        paymentRepository.updateProviderSession(payment);
        return maybeSettle(payment, session);
    }

    private Payment maybeSettle(Payment payment, ProviderPaymentSession session) {
        if (!session.settleImmediately()) {
            log.info(
                    "Payment {} awaiting async provider confirmation (provider={}, providerPaymentId={})",
                    payment.getId().getValue(),
                    session.provider(),
                    session.providerPaymentId()
            );
            return payment;
        }
        return settleImmediately(payment, session);
    }

    private Payment settleImmediately(Payment payment, ProviderPaymentSession session) {
        Instant occurredAt = clock.instant();

        if (session.success()) {
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

        payment.fail(session.failureReason());
        assertStatusUpdated(payment, PaymentStatus.PENDING);
        paymentOutboxPort.enqueueFailed(new PaymentFailedEvent(
                idGenerator.generateId(),
                payment.getId().getValue(),
                payment.getOrderId().getValue(),
                payment.getUserId().value(),
                payment.getAmount(),
                session.failureReason(),
                occurredAt
        ));
        log.info(
                "Payment {} failed for order {}: {}",
                payment.getId().getValue(),
                payment.getOrderId().getValue(),
                session.failureReason()
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
