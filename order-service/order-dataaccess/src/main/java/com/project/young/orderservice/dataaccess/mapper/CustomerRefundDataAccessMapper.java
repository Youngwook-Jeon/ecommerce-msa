package com.project.young.orderservice.dataaccess.mapper;

import com.project.young.orderservice.dataaccess.entity.CustomerRefundEntity;
import com.project.young.orderservice.domain.entity.CustomerRefund;
import com.project.young.orderservice.domain.valueobject.CustomerRefundId;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.springframework.stereotype.Component;

@Component
public class CustomerRefundDataAccessMapper {

    public CustomerRefundEntity toEntity(CustomerRefund customerRefund) {
        return CustomerRefundEntity.builder()
                .refundId(customerRefund.getId().getValue())
                .orderId(customerRefund.getOrderId().getValue())
                .paymentId(customerRefund.getPaymentId())
                .userId(customerRefund.getUserId().value())
                .reason(customerRefund.getReason())
                .status(customerRefund.getStatus())
                .failureReason(customerRefund.getFailureReason())
                .resultVersion(customerRefund.getResultVersion())
                .completedAt(customerRefund.getCompletedAt()).failedAt(customerRefund.getFailedAt())
                .requestedAt(customerRefund.getRequestedAt())
                .updatedAt(customerRefund.getUpdatedAt())
                .build();
    }

    public CustomerRefund toDomain(CustomerRefundEntity entity) {
        return CustomerRefund.restore(
                new CustomerRefundId(entity.getRefundId()),
                new OrderId(entity.getOrderId()),
                entity.getPaymentId(),
                new UserId(entity.getUserId()),
                entity.getReason(),
                entity.getStatus(),
                entity.getFailureReason(),
                entity.getRequestedAt(),
                entity.getUpdatedAt(), entity.getResultVersion(), entity.getCompletedAt(), entity.getFailedAt()
        );
    }
}
