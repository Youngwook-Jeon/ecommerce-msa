package com.project.young.paymentservice.web.dto;

import com.project.young.paymentservice.application.dto.query.OrderPaymentStatusView;

import java.util.List;

public record OrderPaymentStatusBatchResponse(List<OrderPaymentStatusView> payments) {
}
