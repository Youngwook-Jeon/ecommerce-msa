package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.port.output.ProviderEventIdempotencyPort;
import com.project.young.paymentservice.dataaccess.entity.PaymentProviderEventEntity;
import com.project.young.paymentservice.dataaccess.repository.PaymentProviderEventJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
public class ProviderEventIdempotencyAdapter implements ProviderEventIdempotencyPort {

    private final PaymentProviderEventJpaRepository paymentProviderEventJpaRepository;

    public ProviderEventIdempotencyAdapter(PaymentProviderEventJpaRepository paymentProviderEventJpaRepository) {
        this.paymentProviderEventJpaRepository = paymentProviderEventJpaRepository;
    }

    @Override
    @Transactional
    public boolean tryMarkProcessed(String eventId, UUID paymentId, String provider, String eventType) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");

        if (paymentProviderEventJpaRepository.existsById(eventId)) {
            return false;
        }

        try {
            paymentProviderEventJpaRepository.save(PaymentProviderEventEntity.builder()
                    .eventId(eventId)
                    .paymentId(paymentId)
                    .provider(provider)
                    .eventType(eventType)
                    .processedAt(Instant.now())
                    .build());
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }
}
