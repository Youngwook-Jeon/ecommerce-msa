package com.project.young.orderservice.web.order.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderResponse(
        UUID orderId,
        String userId,
        String status,
        BigDecimal subtotal,
        BigDecimal shippingAmount,
        BigDecimal totalAmount,
        ShippingAddressResponse shippingAddress,
        List<OrderLineResponse> lines,
        int lineCount,
        int totalQuantity,
        Instant createdAt,
        Instant updatedAt
) {
    public OrderResponse {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
