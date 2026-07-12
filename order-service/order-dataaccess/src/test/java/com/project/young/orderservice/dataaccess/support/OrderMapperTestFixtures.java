package com.project.young.orderservice.dataaccess.support;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.dataaccess.entity.CartItemOptionLineJson;
import com.project.young.orderservice.dataaccess.entity.OrderEntity;
import com.project.young.orderservice.dataaccess.entity.OrderLineEntity;
import com.project.young.orderservice.dataaccess.enums.OrderStatusEntity;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.entity.OrderLine;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.OrderLineId;
import com.project.young.orderservice.domain.valueobject.OrderStatus;
import com.project.young.orderservice.domain.valueobject.ShippingAddress;
import com.project.young.orderservice.domain.valueobject.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.PRODUCT_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.VARIANT_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.sampleSnapshot;

public final class OrderMapperTestFixtures {

    public static final UserId USER_ID = new UserId("keycloak-sub-order-test");
    public static final OrderId ORDER_ID = new OrderId(UUID.fromString("018f0000-0000-7000-8000-000000000601"));
    public static final OrderLineId LINE_ID = new OrderLineId(UUID.fromString("018f0000-0000-7000-8000-000000000602"));

    private OrderMapperTestFixtures() {
    }

    public static ShippingAddress sampleShippingAddress() {
        return new ShippingAddress(
                "Kim Young",
                "01012345678",
                "123 Main St",
                "Apt 4B",
                "Seoul",
                "04524",
                "KR"
        );
    }

    public static OrderLine domainOrderLine(
            OrderLineId lineId,
            ProductId productId,
            ProductVariantId variantId,
            CartItemSnapshot snapshot,
            int quantity
    ) {
        return OrderLine.reconstitute(lineId, productId, variantId, snapshot, quantity);
    }

    public static Order domainOrderWithOneLine() {
        OrderLine line = domainOrderLine(
                LINE_ID,
                PRODUCT_ID,
                VARIANT_ID,
                sampleSnapshot("999.00"),
                2
        );

        return Order.builder()
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .status(OrderStatus.CONFIRMED)
                .shippingAddress(sampleShippingAddress())
                .lines(List.of(line))
                .subtotalAmount(new Money(new BigDecimal("1998.00")))
                .shippingAmount(Money.ZERO)
                .totalAmount(new Money(new BigDecimal("1998.00")))
                .build();
    }

    public static OrderEntity persistedOrderEntity(UUID orderId, String userId, List<OrderLineEntity> lines) {
        OrderEntity order = OrderEntity.builder()
                .id(orderId)
                .userId(userId)
                .status(OrderStatusEntity.CONFIRMED)
                .subtotalAmount(new BigDecimal("1998.00"))
                .shippingAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("1998.00"))
                .shippingRecipientName("Kim Young")
                .shippingPhone("01012345678")
                .shippingAddressLine1("123 Main St")
                .shippingAddressLine2("Apt 4B")
                .shippingCity("Seoul")
                .shippingPostalCode("04524")
                .shippingCountryCode("KR")
                .build();
        for (OrderLineEntity line : lines) {
            order.addLine(line);
        }
        return order;
    }

    public static OrderLineEntity persistedLineEntity(
            UUID lineId,
            UUID productId,
            UUID variantId,
            String productName,
            BigDecimal unitPrice,
            int quantity
    ) {
        return OrderLineEntity.builder()
                .id(lineId)
                .productId(productId)
                .productVariantId(variantId)
                .productName(productName)
                .brand("Apple")
                .sku("SKU-001")
                .imageUrl("https://cdn.example.com/image.jpg")
                .unitPrice(unitPrice)
                .variantOptionsJson(List.of(CartItemOptionLineJson.builder()
                        .stepOrder(1)
                        .productOptionGroupId(UUID.fromString("018f0000-0000-7000-8000-000000000401"))
                        .optionGroupName("Color")
                        .productOptionValueId(UUID.fromString("018f0000-0000-7000-8000-000000000501"))
                        .optionValueName("Black")
                        .build()))
                .quantity(quantity)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
