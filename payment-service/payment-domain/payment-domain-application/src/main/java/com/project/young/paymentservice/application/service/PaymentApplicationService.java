package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.ApplyProviderPaymentResultCommand;
import com.project.young.paymentservice.application.dto.command.ProcessPaymentCommand;
import com.project.young.paymentservice.application.dto.event.PaymentCompletedEvent;
import com.project.young.paymentservice.application.dto.event.PaymentFailedEvent;
import com.project.young.paymentservice.application.dto.query.ClientSecretView;
import com.project.young.paymentservice.application.port.output.IdGenerator;
import com.project.young.paymentservice.application.port.output.PaymentOutboxPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.ProviderPaymentSession;
import com.project.young.paymentservice.application.port.output.ProviderEventIdempotencyPort;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.exception.PaymentDomainException;
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
    private final IdGenerator idGenerator;
    private final Clock clock;

    public PaymentApplicationService(
            PaymentRepository paymentRepository,
            PaymentOutboxPort paymentOutboxPort,
            PaymentProviderPort paymentProviderPort,
            ProviderEventIdempotencyPort providerEventIdempotencyPort,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentOutboxPort = paymentOutboxPort;
        this.paymentProviderPort = paymentProviderPort;
        this.providerEventIdempotencyPort = providerEventIdempotencyPort;
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
            return ensureProviderSessionAndMaybeSettle(payment, false);
        }

        PaymentId paymentId = new PaymentId(idGenerator.generateId());
        Payment payment = Payment.createPending(
                paymentId,
                orderId,
                new UserId(command.userId()),
                command.amount(),
                command.currency()
        );
        return ensureProviderSessionAndMaybeSettle(payment, true);
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
                command.success() ? "PAYMENT_SUCCEEDED" : "PAYMENT_FAILED"
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
    public ClientSecretView getClientSecretByOrderId(UUID orderIdValue) {
        Objects.requireNonNull(orderIdValue, "orderId must not be null");
        Payment payment = paymentRepository.findByOrderId(new OrderId(orderIdValue))
                .orElseThrow(() -> new PaymentDomainException("Payment not found for order " + orderIdValue));

        if (payment.getClientSecret() == null || payment.getClientSecret().isBlank()) {
            throw new PaymentDomainException(
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

    private Payment ensureProviderSessionAndMaybeSettle(Payment payment, boolean isNew) {
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
        if (isNew) {
            paymentRepository.insert(payment);
            log.info(
                    "Created pending payment {} for order {} with provider {}",
                    payment.getId().getValue(),
                    payment.getOrderId().getValue(),
                    session.provider()
            );
        } else {
            paymentRepository.updateProviderSession(payment);
        }
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
