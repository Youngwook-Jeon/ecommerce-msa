package com.project.young.orderservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.dataaccess.entity.CartItemOptionLineJson;
import com.project.young.orderservice.dataaccess.entity.OrderEntity;
import com.project.young.orderservice.dataaccess.entity.OrderLineEntity;
import com.project.young.orderservice.dataaccess.enums.OrderStatusEntity;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.entity.OrderLine;
import com.project.young.orderservice.domain.valueobject.CartItemOptionLine;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.OrderLineId;
import com.project.young.orderservice.domain.valueobject.OrderStatus;
import com.project.young.orderservice.domain.valueobject.ShippingAddress;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderAggregateMapper {

    public Order toOrder(OrderEntity entity) {
        if (entity == null) {
            return null;
        }

        List<OrderLine> lines = entity.getLines().stream()
                .map(this::toOrderLine)
                .toList();

        return Order.builder()
                .orderId(new OrderId(entity.getId()))
                .userId(new UserId(entity.getUserId()))
                .status(toDomainStatus(entity.getStatus()))
                .shippingAddress(toShippingAddress(entity))
                .lines(lines)
                .subtotalAmount(new Money(entity.getSubtotalAmount()))
                .shippingAmount(new Money(entity.getShippingAmount()))
                .totalAmount(new Money(entity.getTotalAmount()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private OrderLine toOrderLine(OrderLineEntity entity) {
        return OrderLine.reconstitute(
                new OrderLineId(entity.getId()),
                new ProductId(entity.getProductId()),
                new ProductVariantId(entity.getProductVariantId()),
                toSnapshot(entity),
                entity.getQuantity()
        );
    }

    private CartItemSnapshot toSnapshot(OrderLineEntity entity) {
        return new CartItemSnapshot(
                entity.getProductName(),
                entity.getBrand(),
                entity.getSku(),
                entity.getImageUrl(),
                new Money(entity.getUnitPrice()),
                toOptionLines(entity.getVariantOptionsJson())
        );
    }

    private List<CartItemOptionLine> toOptionLines(List<CartItemOptionLineJson> jsonLines) {
        if (jsonLines == null || jsonLines.isEmpty()) {
            return List.of();
        }
        return jsonLines.stream()
                .map(json -> new CartItemOptionLine(
                        json.getStepOrder(),
                        json.getProductOptionGroupId(),
                        json.getOptionGroupName(),
                        json.getProductOptionValueId(),
                        json.getOptionValueName()
                ))
                .toList();
    }

    private ShippingAddress toShippingAddress(OrderEntity entity) {
        return new ShippingAddress(
                entity.getShippingRecipientName(),
                entity.getShippingPhone(),
                entity.getShippingAddressLine1(),
                entity.getShippingAddressLine2(),
                entity.getShippingCity(),
                entity.getShippingPostalCode(),
                entity.getShippingCountryCode()
        );
    }

    private OrderStatus toDomainStatus(OrderStatusEntity entityStatus) {
        return OrderStatus.valueOf(entityStatus.name());
    }
}
