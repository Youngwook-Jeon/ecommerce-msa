package com.project.young.orderservice.web.order.mapper;

import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.entity.OrderLine;
import com.project.young.orderservice.domain.valueobject.CartItemOptionLine;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.ShippingAddress;
import com.project.young.orderservice.web.cart.dto.CartItemOptionResponse;
import com.project.young.orderservice.web.converter.OrderStatusWebConverter;
import com.project.young.orderservice.web.order.dto.OrderLineResponse;
import com.project.young.orderservice.web.order.dto.OrderResponse;
import com.project.young.orderservice.web.order.dto.ShippingAddressResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class OrderResponseMapper {

    private final OrderStatusWebConverter orderStatusWebConverter;

    public OrderResponseMapper(OrderStatusWebConverter orderStatusWebConverter) {
        this.orderStatusWebConverter = orderStatusWebConverter;
    }

    public OrderResponse toResponse(Order order) {
        List<OrderLineResponse> lines = order.getLines().stream()
                .map(this::toLineResponse)
                .toList();

        return OrderResponse.builder()
                .orderId(order.getId().getValue())
                .userId(order.getUserId().value())
                .status(orderStatusWebConverter.toStringValue(order.getStatus()))
                .subtotal(order.getSubtotalAmount().getAmount())
                .shippingAmount(order.getShippingAmount().getAmount())
                .totalAmount(order.getTotalAmount().getAmount())
                .shippingAddress(toShippingAddressResponse(order.getShippingAddress()))
                .lines(lines)
                .lineCount(order.lineCount())
                .totalQuantity(order.totalQuantity())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderLineResponse toLineResponse(OrderLine line) {
        CartItemSnapshot snapshot = line.getSnapshot();
        return OrderLineResponse.builder()
                .lineId(line.getId().getValue())
                .productId(line.getProductId().getValue())
                .productVariantId(line.getProductVariantId().getValue())
                .productName(snapshot.productName())
                .brand(snapshot.brand())
                .sku(snapshot.sku())
                .imageUrl(snapshot.imageUrl())
                .unitPrice(snapshot.unitPrice().getAmount())
                .quantity(line.getQuantity())
                .lineAmount(line.lineAmount().getAmount())
                .variantOptions(toOptionResponses(snapshot.variantOptions()))
                .build();
    }

    private List<CartItemOptionResponse> toOptionResponses(List<CartItemOptionLine> options) {
        return options.stream()
                .map(option -> CartItemOptionResponse.builder()
                        .stepOrder(option.stepOrder())
                        .productOptionGroupId(option.productOptionGroupId())
                        .optionGroupName(option.optionGroupName())
                        .productOptionValueId(option.productOptionValueId())
                        .optionValueName(option.optionValueName())
                        .build())
                .toList();
    }

    private ShippingAddressResponse toShippingAddressResponse(ShippingAddress address) {
        return ShippingAddressResponse.builder()
                .recipientName(address.recipientName())
                .phone(address.phone())
                .addressLine1(address.addressLine1())
                .addressLine2(address.addressLine2())
                .city(address.city())
                .postalCode(address.postalCode())
                .countryCode(address.countryCode())
                .build();
    }
}
