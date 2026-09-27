package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.port.output.CustomerRefundProcessingPort;
import com.project.young.paymentservice.dataaccess.repository.CustomerRefundProcessingJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class CustomerRefundProcessingAdapter implements CustomerRefundProcessingPort {

    private final CustomerRefundProcessingJpaRepository repository;

    public CustomerRefundProcessingAdapter(CustomerRefundProcessingJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isProcessed(UUID refundId) {
        return repository.existsByRefundId(refundId);
    }

    @Override
    @Transactional
    public boolean recordProcessed(UUID refundId, UUID paymentId, UUID orderId, String userId) {
        return repository.insert(refundId, paymentId, orderId, userId, Instant.now()) == 1;
    }
}
