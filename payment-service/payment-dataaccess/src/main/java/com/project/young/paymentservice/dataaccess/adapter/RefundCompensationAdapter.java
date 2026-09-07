package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.port.output.RefundCompensationPort;
import com.project.young.paymentservice.dataaccess.repository.PaymentRefundCompensationJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class RefundCompensationAdapter implements RefundCompensationPort {

    private final PaymentRefundCompensationJpaRepository paymentRefundCompensationJpaRepository;

    public RefundCompensationAdapter(PaymentRefundCompensationJpaRepository paymentRefundCompensationJpaRepository) {
        this.paymentRefundCompensationJpaRepository = paymentRefundCompensationJpaRepository;
    }

    @Override
    public boolean isProcessed(UUID compensationEventId) {
        return paymentRefundCompensationJpaRepository.existsById(compensationEventId);
    }

    @Override
    @Transactional
    public boolean recordProcessed(UUID compensationEventId, UUID paymentId, UUID orderId) {
        return paymentRefundCompensationJpaRepository.insertIfAbsent(
                compensationEventId,
                paymentId,
                orderId,
                Instant.now()
        ) == 1;
    }
}
