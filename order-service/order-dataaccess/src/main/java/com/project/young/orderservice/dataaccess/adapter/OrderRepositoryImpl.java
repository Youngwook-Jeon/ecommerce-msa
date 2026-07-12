package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.dataaccess.entity.OrderEntity;
import com.project.young.orderservice.dataaccess.mapper.OrderAggregateMapper;
import com.project.young.orderservice.dataaccess.mapper.OrderDataAccessMapper;
import com.project.young.orderservice.dataaccess.repository.OrderJpaRepository;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.entity.OrderLine;
import com.project.young.orderservice.domain.repository.OrderRepository;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.UserId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;
    private final OrderDataAccessMapper orderDataAccessMapper;
    private final OrderAggregateMapper orderAggregateMapper;
    private final EntityManager entityManager;

    public OrderRepositoryImpl(
            OrderJpaRepository orderJpaRepository,
            OrderDataAccessMapper orderDataAccessMapper,
            OrderAggregateMapper orderAggregateMapper,
            EntityManager entityManager
    ) {
        this.orderJpaRepository = orderJpaRepository;
        this.orderDataAccessMapper = orderDataAccessMapper;
        this.orderAggregateMapper = orderAggregateMapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void insert(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (order.getId() == null) {
            throw new IllegalArgumentException("order id must not be null for insert");
        }
        for (OrderLine line : order.getLines()) {
            if (line.getId() == null) {
                throw new IllegalArgumentException("order line id must not be null for insert");
            }
        }

        OrderEntity toPersist = orderDataAccessMapper.orderToOrderEntity(order);
        entityManager.persist(toPersist);
    }

    @Override
    public Optional<Order> findById(OrderId orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        return orderJpaRepository.findWithLinesById(orderId.getValue()).map(orderAggregateMapper::toOrder);
    }

    @Override
    public Optional<Order> findByIdAndUserId(OrderId orderId, UserId userId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return orderJpaRepository.findWithLinesByIdAndUserId(orderId.getValue(), userId.value())
                .map(orderAggregateMapper::toOrder);
    }
}
