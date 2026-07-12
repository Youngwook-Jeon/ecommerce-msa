package com.project.young.orderservice.dataaccess.mapper;

import com.project.young.orderservice.dataaccess.entity.CartItemOptionLineJson;
import com.project.young.orderservice.dataaccess.entity.OrderEntity;
import com.project.young.orderservice.dataaccess.entity.OrderLineEntity;
import com.project.young.orderservice.dataaccess.enums.OrderStatusEntity;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.entity.OrderLine;
import com.project.young.orderservice.domain.valueobject.CartItemOptionLine;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OrderDataAccessMapper {

    public OrderEntity orderToOrderEntity(Order order) {
        OrderEntity entity = OrderEntity.builder()
                .id(order.getId().getValue())
                .userId(order.getUserId().value())
                .status(toEntityStatus(order.getStatus()))
                .subtotalAmount(order.getSubtotalAmount().getAmount())
                .shippingAmount(order.getShippingAmount().getAmount())
                .totalAmount(order.getTotalAmount().getAmount())
                .shippingRecipientName(order.getShippingAddress().recipientName())
                .shippingPhone(order.getShippingAddress().phone())
                .shippingAddressLine1(order.getShippingAddress().addressLine1())
                .shippingAddressLine2(order.getShippingAddress().addressLine2())
                .shippingCity(order.getShippingAddress().city())
                .shippingPostalCode(order.getShippingAddress().postalCode())
                .shippingCountryCode(order.getShippingAddress().countryCode())
                .build();

        for (OrderLine line : order.getLines()) {
            OrderLineEntity lineEntity = new OrderLineEntity();
            lineEntity.setOrder(entity);
            applyLine(line, lineEntity);
            entity.addLine(lineEntity);
        }

        return entity;
    }

    private void applyLine(OrderLine line, OrderLineEntity entity) {
        entity.setId(line.getId().getValue());
        entity.setProductId(line.getProductId().getValue());
        entity.setProductVariantId(line.getProductVariantId().getValue());
        applySnapshot(entity, line.getSnapshot());
        entity.setQuantity(line.getQuantity());
    }

    private void applySnapshot(OrderLineEntity entity, CartItemSnapshot snapshot) {
        entity.setProductName(snapshot.productName());
        entity.setBrand(snapshot.brand());
        entity.setSku(snapshot.sku());
        entity.setImageUrl(snapshot.imageUrl());
        entity.setUnitPrice(snapshot.unitPrice().getAmount());
        entity.setVariantOptionsJson(toJsonLines(snapshot.variantOptions()));
    }

    private List<CartItemOptionLineJson> toJsonLines(List<CartItemOptionLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return new ArrayList<>();
        }
        List<CartItemOptionLineJson> jsonLines = new ArrayList<>(lines.size());
        for (CartItemOptionLine line : lines) {
            jsonLines.add(CartItemOptionLineJson.builder()
                    .stepOrder(line.stepOrder())
                    .productOptionGroupId(line.productOptionGroupId())
                    .optionGroupName(line.optionGroupName())
                    .productOptionValueId(line.productOptionValueId())
                    .optionValueName(line.optionValueName())
                    .build());
        }
        return jsonLines;
    }

    public OrderStatusEntity toEntityStatus(OrderStatus domainStatus) {
        return OrderStatusEntity.valueOf(domainStatus.name());
    }

    public OrderStatus toDomainStatus(OrderStatusEntity entityStatus) {
        return OrderStatus.valueOf(entityStatus.name());
    }
}
