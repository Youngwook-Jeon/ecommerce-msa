package com.project.young.orderservice.domain.repository;

import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.UserId;

import java.util.Optional;

public interface OrderRepository {

    void insert(Order order);

    Optional<Order> findById(OrderId orderId);

    Optional<Order> findByIdAndUserId(OrderId orderId, UserId userId);
}
