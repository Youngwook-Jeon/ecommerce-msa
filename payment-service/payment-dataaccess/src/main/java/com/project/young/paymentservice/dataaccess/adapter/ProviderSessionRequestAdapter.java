package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.port.output.ProviderSessionRequestPort;
import com.project.young.paymentservice.application.provider.ProviderSessionRequestStatus;
import com.project.young.paymentservice.application.dto.query.ProviderSessionRequestEscalationView;
import com.project.young.paymentservice.dataaccess.entity.ProviderSessionRequestEntity;
import com.project.young.paymentservice.dataaccess.repository.ProviderSessionRequestJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Repository
@Transactional
public class ProviderSessionRequestAdapter implements ProviderSessionRequestPort {

    private final ProviderSessionRequestJpaRepository repository;

    public ProviderSessionRequestAdapter(ProviderSessionRequestJpaRepository repository) {
        this.repository = repository;
    }

    public void enqueue(UUID id) {
        repository.enqueue(id, ProviderSessionRequestStatus.PENDING.name(), Instant.now());
    }

    public List<UUID> claimPending(int limit) {
        return repository.findTop100ByStatusOrderByCreatedAt(ProviderSessionRequestStatus.PENDING)
                .stream()
                .limit(limit)
                .map(ProviderSessionRequestEntity::getPaymentId)
                .filter(id -> repository.claim(id, ProviderSessionRequestStatus.PENDING.name(), ProviderSessionRequestStatus.PROCESSING.name(), Instant.now()) == 1).toList();
    }

    public void complete(UUID id) {
        repository.complete(id, ProviderSessionRequestStatus.COMPLETED.name(), Instant.now());
    }

    public void release(UUID id, String message) {
        repository.release(id, ProviderSessionRequestStatus.PENDING.name(), message == null ? null : message.substring(0, Math.min(message.length(), 4096)), Instant.now());
    }

    public void escalate(UUID id, String message) {
        repository.escalate(id, ProviderSessionRequestStatus.ESCALATED.name(), message == null ? null : message.substring(0, Math.min(message.length(), 4096)), Instant.now());
    }

    public int releaseExpiredProcessing(Instant threshold) {
        return repository.releaseExpired(threshold, Instant.now());
    }

    public boolean hasReachedAttemptLimit(UUID id, int maxAttempts) {
        return repository.findById(id).map(request -> request.getAttempts() >= maxAttempts).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderSessionRequestEscalationView> findEscalated(int limit) {
        return repository.findTop100ByStatusOrderByUpdatedAt(ProviderSessionRequestStatus.ESCALATED).stream()
                .limit(limit)
                .map(request -> new ProviderSessionRequestEscalationView(
                        request.getPaymentId(),
                        request.getAttempts(),
                        request.getFailureMessage(),
                        request.getCreatedAt(),
                        request.getUpdatedAt()
                ))
                .toList();
    }
}
