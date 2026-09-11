package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.dto.command.ApplyProviderPaymentResultCommand;
import com.project.young.paymentservice.application.dto.query.ProviderWebhookInboxEscalationView;
import com.project.young.paymentservice.application.port.output.ProviderWebhookInboxPort;
import com.project.young.paymentservice.application.provider.ProviderWebhookInboxStatus;
import com.project.young.paymentservice.dataaccess.entity.ProviderWebhookInboxEntity;
import com.project.young.paymentservice.dataaccess.repository.ProviderWebhookInboxJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
@Transactional
public class ProviderWebhookInboxAdapter implements ProviderWebhookInboxPort {

    private static final int FAILURE_MESSAGE_MAX_LENGTH = 4096;

    private final ProviderWebhookInboxJpaRepository repository;

    public ProviderWebhookInboxAdapter(ProviderWebhookInboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void recordReceived(ApplyProviderPaymentResultCommand command) {
        repository.insertIfAbsent(
                command.eventId(),
                command.provider().name(),
                command.providerPaymentId(),
                command.success(),
                command.outcome().name(),
                truncate(command.failureReason()),
                ProviderWebhookInboxStatus.RECEIVED.name(),
                Instant.now()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplyProviderPaymentResultCommand> findReady(int limit) {
        return repository.findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByReceivedAt(List.of(
                        ProviderWebhookInboxStatus.RECEIVED,
                        ProviderWebhookInboxStatus.WAITING_FOR_PAYMENT
                ), Instant.now()).stream()
                .limit(limit)
                .map(this::toCommand)
                .toList();
    }

    @Override
    public boolean claim(String eventId, Instant startedAt) {
        return repository.claim(
                eventId,
                ProviderWebhookInboxStatus.RECEIVED.name(),
                ProviderWebhookInboxStatus.WAITING_FOR_PAYMENT.name(),
                ProviderWebhookInboxStatus.PROCESSING.name(),
                startedAt
        ) == 1;
    }

    @Override
    public void markApplied(String eventId) {
        repository.markApplied(eventId, ProviderWebhookInboxStatus.PROCESSING.name(),
                ProviderWebhookInboxStatus.APPLIED.name(), Instant.now());
    }

    @Override
    public void returnToWaiting(String eventId, String failureMessage, Instant nextRetryAt) {
        repository.returnToWaiting(eventId, ProviderWebhookInboxStatus.PROCESSING.name(),
                ProviderWebhookInboxStatus.WAITING_FOR_PAYMENT.name(), truncate(failureMessage), nextRetryAt, Instant.now());
    }

    @Override
    public void escalate(String eventId, String failureMessage) {
        repository.escalate(eventId, ProviderWebhookInboxStatus.PROCESSING.name(),
                ProviderWebhookInboxStatus.ESCALATED.name(), truncate(failureMessage), Instant.now());
    }

    @Override
    public int returnExpiredProcessingToWaiting(Instant threshold) {
        return repository.releaseExpired(ProviderWebhookInboxStatus.PROCESSING.name(),
                ProviderWebhookInboxStatus.WAITING_FOR_PAYMENT.name(), threshold, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasReachedAttemptLimit(String eventId, int maxAttempts) {
        return repository.findById(eventId)
                .map(inbox -> inbox.getAttempts() >= maxAttempts)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public int attempts(String eventId) {
        return repository.findById(eventId)
                .map(ProviderWebhookInboxEntity::getAttempts)
                .orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderWebhookInboxEscalationView> findEscalated(int limit) {
        return repository.findTop100ByStatusOrderByUpdatedAt(ProviderWebhookInboxStatus.ESCALATED).stream()
                .limit(limit)
                .map(inbox -> new ProviderWebhookInboxEscalationView(
                        inbox.getEventId(),
                        inbox.getProvider(),
                        inbox.getProviderPaymentId(),
                        inbox.getOutcome().name(),
                        inbox.getAttempts(),
                        inbox.getLastFailureMessage(),
                        inbox.getReceivedAt(),
                        inbox.getUpdatedAt()
                ))
                .toList();
    }

    private ApplyProviderPaymentResultCommand toCommand(ProviderWebhookInboxEntity inbox) {
        return new ApplyProviderPaymentResultCommand(
                inbox.getEventId(),
                com.project.young.paymentservice.domain.valueobject.PaymentProvider.valueOf(inbox.getProvider()),
                inbox.getProviderPaymentId(),
                inbox.getOutcome(),
                inbox.getFailureReason()
        );
    }

    private static String truncate(String value) {
        return value == null || value.length() <= FAILURE_MESSAGE_MAX_LENGTH
                ? value
                : value.substring(0, FAILURE_MESSAGE_MAX_LENGTH);
    }
}
