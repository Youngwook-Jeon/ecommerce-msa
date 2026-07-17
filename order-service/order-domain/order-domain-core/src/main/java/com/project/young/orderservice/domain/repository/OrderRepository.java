package com.project.young.orderservice.domain.repository;

import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.OrderStatus;
import com.project.young.orderservice.domain.valueobject.UserId;

import java.util.Optional;

public interface OrderRepository {

    void insert(Order order);

    /**
     * Atomically changes status only when the persisted status still equals {@code expectedStatus}.
     */
    boolean updateStatus(Order order, OrderStatus expectedStatus);

    Optional<Order> findById(OrderId orderId);

    Optional<Order> findByIdAndUserId(OrderId orderId, UserId userId);
}
