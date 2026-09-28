package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.dto.command.ObserveProviderRefundCommand;
import com.project.young.paymentservice.application.port.output.ProviderRefundWebhookInboxPort;
import com.project.young.paymentservice.dataaccess.repository.ProviderRefundWebhookInboxJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public class ProviderRefundWebhookInboxAdapter implements ProviderRefundWebhookInboxPort {
    private final ProviderRefundWebhookInboxJpaRepository repository;

    public ProviderRefundWebhookInboxAdapter(ProviderRefundWebhookInboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public boolean recordReceived(ObserveProviderRefundCommand command) {
        String reason = command.failureReason();
        return repository.insertIfAbsent(command,
                reason == null || reason.length() <= 500 ? reason : reason.substring(0, 500)) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObserveProviderRefundCommand> findReady(int limit, Instant now, Instant expiredBefore) {
        return repository.findReady(now, expiredBefore, PageRequest.of(0, limit)).stream()
                .map(e -> new ObserveProviderRefundCommand(e.getEventId(), e.getProviderRefundId(),
                        e.getProviderPaymentId(), e.getRefundState(), e.getFailureReason())).toList();
    }

    @Override
    @Transactional
    public boolean claim(String eventId, Instant now, Instant expiredBefore) {
        return repository.claim(eventId, now, expiredBefore) == 1;
    }

    @Override
    @Transactional
    public void markApplied(String eventId, Instant claimedAt) {
        repository.markApplied(eventId, claimedAt);
    }

    @Override
    @Transactional
    public void retryOrEscalate(String eventId, Instant claimedAt, int maxAttempts,
                                Instant nextAttemptAt, String exceptionClass) {
        repository.retryOrEscalate(eventId, claimedAt, maxAttempts, nextAttemptAt,
                exceptionClass.length() <= 1024 ? exceptionClass : exceptionClass.substring(0, 1024));
    }
}
