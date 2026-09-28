package com.project.young.paymentservice.dataaccess.adapter;

import java.util.UUID;

import com.project.young.paymentservice.application.dto.command.EscalateCustomerRefundCommand;
import com.project.young.paymentservice.application.port.output.CustomerRefundReviewPort;
import com.project.young.paymentservice.dataaccess.repository.CustomerRefundReviewJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
public class CustomerRefundReviewAdapter implements CustomerRefundReviewPort {

    private final CustomerRefundReviewJpaRepository repository;

    public CustomerRefundReviewAdapter(CustomerRefundReviewJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public boolean recordIfAbsent(EscalateCustomerRefundCommand command) {
        return repository.insertIfAbsent(command.refundId(), command.paymentId(), command.providerRefundId(),
                truncate(command.failureExceptionClass(), 1024), truncate(command.failureMessage(), 4096),
                Instant.now()) == 1;
    }

    private static String truncate(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }

    @Override
    @Transactional
    public void recordConfirmedLateFailure(UUID refundId, UUID paymentId,
                                           String providerRefundId, String reason) {
        repository.recordConfirmedLateFailure(refundId, paymentId, providerRefundId, truncate(reason, 500));
    }
}
