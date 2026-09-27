package com.project.young.orderservice.web.refund.mapper;

import com.project.young.orderservice.application.dto.CustomerRefundView;
import com.project.young.orderservice.web.refund.dto.CustomerRefundResponse;
import org.springframework.stereotype.Component;

@Component
public class CustomerRefundResponseMapper {

    public CustomerRefundResponse toResponse(CustomerRefundView customerRefund) {
        return CustomerRefundResponse.builder()
                .refundId(customerRefund.refundId())
                .orderId(customerRefund.orderId())
                .paymentId(customerRefund.paymentId())
                .reason(customerRefund.reason())
                .status(customerRefund.status().name())
                .failureReason(customerRefund.failureReason())
                .requestedAt(customerRefund.requestedAt())
                .updatedAt(customerRefund.updatedAt())
                .build();
    }
}
