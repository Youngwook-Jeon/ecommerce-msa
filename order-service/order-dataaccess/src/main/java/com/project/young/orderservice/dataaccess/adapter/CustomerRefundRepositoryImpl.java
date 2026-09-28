package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.dataaccess.entity.CustomerRefundEntity;
import com.project.young.orderservice.dataaccess.mapper.CustomerRefundDataAccessMapper;
import com.project.young.orderservice.dataaccess.repository.CustomerRefundJpaRepository;
import com.project.young.orderservice.domain.entity.CustomerRefund;
import com.project.young.orderservice.domain.repository.CustomerRefundRepository;
import com.project.young.orderservice.domain.valueobject.CustomerRefundId;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.UserId;
import com.project.young.orderservice.domain.valueobject.CustomerRefundStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class CustomerRefundRepositoryImpl implements CustomerRefundRepository {

    private final CustomerRefundJpaRepository customerRefundJpaRepository;
    private final CustomerRefundDataAccessMapper customerRefundDataAccessMapper;
    private final EntityManager entityManager;

    public CustomerRefundRepositoryImpl(
            CustomerRefundJpaRepository customerRefundJpaRepository,
            CustomerRefundDataAccessMapper customerRefundDataAccessMapper,
            EntityManager entityManager
    ) {
        this.customerRefundJpaRepository = customerRefundJpaRepository;
        this.customerRefundDataAccessMapper = customerRefundDataAccessMapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void insert(CustomerRefund customerRefund) {
        if (customerRefund == null || customerRefund.getId() == null) {
            throw new IllegalArgumentException("customerRefund and refund id must not be null");
        }
        CustomerRefundEntity entity = customerRefundDataAccessMapper.toEntity(customerRefund);
        entityManager.persist(entity);
    }

    @Override
    public Optional<CustomerRefund> findByIdAndUserId(CustomerRefundId refundId, UserId userId) {
        if (refundId == null) {
            throw new IllegalArgumentException("refundId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return customerRefundJpaRepository.findByRefundIdAndUserId(refundId.getValue(), userId.value())
                .map(customerRefundDataAccessMapper::toDomain);
    }

    @Override
    public Optional<CustomerRefund> findByOrderId(OrderId orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        return customerRefundJpaRepository.findByOrderId(orderId.getValue())
                .map(customerRefundDataAccessMapper::toDomain);
    }

    @Override
    @Transactional
    public boolean updateResultIfVersion(CustomerRefund refund, long expectedVersion, CustomerRefundStatus expectedStatus) {
        return customerRefundJpaRepository.updateResultIfVersion(refund, expectedVersion, expectedStatus) == 1;
    }
}
