package com.project.young.orderservice.domain.repository;

import com.project.young.orderservice.domain.entity.CustomerRefund;
import com.project.young.orderservice.domain.valueobject.CustomerRefundId;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.UserId;

import java.util.Optional;

public interface CustomerRefundRepository {

    void insert(CustomerRefund customerRefund);

    Optional<CustomerRefund> findByIdAndUserId(CustomerRefundId refundId, UserId userId);

    Optional<CustomerRefund> findByOrderId(OrderId orderId);
}
