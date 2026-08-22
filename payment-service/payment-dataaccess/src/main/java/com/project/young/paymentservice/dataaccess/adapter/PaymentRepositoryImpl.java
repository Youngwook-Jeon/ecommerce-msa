package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.dataaccess.entity.PaymentEntity;
import com.project.young.paymentservice.dataaccess.mapper.PaymentAggregateMapper;
import com.project.young.paymentservice.dataaccess.mapper.PaymentDataAccessMapper;
import com.project.young.paymentservice.dataaccess.repository.PaymentJpaRepository;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.repository.PaymentRepository;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;
    private final PaymentDataAccessMapper paymentDataAccessMapper;
    private final PaymentAggregateMapper paymentAggregateMapper;
    private final EntityManager entityManager;

    public PaymentRepositoryImpl(
            PaymentJpaRepository paymentJpaRepository,
            PaymentDataAccessMapper paymentDataAccessMapper,
            PaymentAggregateMapper paymentAggregateMapper,
            EntityManager entityManager
    ) {
        this.paymentJpaRepository = paymentJpaRepository;
        this.paymentDataAccessMapper = paymentDataAccessMapper;
        this.paymentAggregateMapper = paymentAggregateMapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void insert(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("payment must not be null");
        }
        if (payment.getId() == null) {
            throw new IllegalArgumentException("payment id must not be null for insert");
        }

        PaymentEntity toPersist = paymentDataAccessMapper.paymentToPaymentEntity(payment);
        entityManager.persist(toPersist);
    }

    @Override
    @Transactional
    public boolean updateStatus(Payment payment, PaymentStatus expectedStatus) {
        if (payment == null || payment.getId() == null) {
            throw new IllegalArgumentException("payment and payment id must not be null");
        }
        if (expectedStatus == null) {
            throw new IllegalArgumentException("expectedStatus must not be null");
        }
        return paymentJpaRepository.updateStatusIfCurrent(
                payment.getId().getValue(),
                paymentDataAccessMapper.toEntityStatus(expectedStatus),
                paymentDataAccessMapper.toEntityStatus(payment.getStatus()),
                payment.getFailureReason(),
                Instant.now()
        ) == 1;
    }

    @Override
    @Transactional
    public void updateProviderSession(Payment payment) {
        if (payment == null || payment.getId() == null) {
            throw new IllegalArgumentException("payment and payment id must not be null");
        }
        if (payment.getProvider() == null
                || payment.getProviderPaymentId() == null
                || payment.getClientSecret() == null) {
            throw new IllegalArgumentException("provider session fields must not be null");
        }

        int updated = paymentJpaRepository.updateProviderSession(
                payment.getId().getValue(),
                payment.getProvider().name(),
                payment.getProviderPaymentId(),
                payment.getClientSecret(),
                Instant.now()
        );
        if (updated != 1) {
            throw new IllegalStateException(
                    "Failed to update provider session for payment " + payment.getId().getValue());
        }
    }

    @Override
    public Optional<Payment> findById(PaymentId paymentId) {
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId must not be null");
        }
        return paymentJpaRepository.findById(paymentId.getValue()).map(paymentAggregateMapper::toPayment);
    }

    @Override
    public Optional<Payment> findByOrderId(OrderId orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        return paymentJpaRepository.findByOrderId(orderId.getValue()).map(paymentAggregateMapper::toPayment);
    }

    @Override
    public Optional<Payment> findByProviderPaymentId(String provider, String providerPaymentId) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new IllegalArgumentException("providerPaymentId must not be blank");
        }
        return paymentJpaRepository.findByProviderAndProviderPaymentId(provider, providerPaymentId)
                .map(paymentAggregateMapper::toPayment);
    }
}
