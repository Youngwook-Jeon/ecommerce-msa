package com.project.young.orderservice.domain.entity;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.domain.exception.OrderDomainException;
import com.project.young.orderservice.domain.valueobject.CartItemId;
import com.project.young.orderservice.domain.valueobject.CartItemOptionLine;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.OrderLineId;
import com.project.young.orderservice.domain.valueobject.OrderStatus;
import com.project.young.orderservice.domain.valueobject.ShippingAddress;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final UserId USER_ID = new UserId("keycloak-sub-order-1");
    private static final OrderId ORDER_ID = new OrderId(UUID.randomUUID());
    private static final ProductId PRODUCT_ID = new ProductId(UUID.randomUUID());
    private static final ProductVariantId VARIANT_ID = new ProductVariantId(UUID.randomUUID());

    @Test
    @DisplayName("placeConfirmed: CONFIRMED 주문을 생성하고 무료 배송 금액을 계산한다")
    void placeConfirmed_createsConfirmedOrderWithFreeShipping() {
        OrderLine line = sampleOrderLine(new OrderLineId(UUID.randomUUID()), 2);

        Order order = Order.placeConfirmed(ORDER_ID, USER_ID, List.of(line), sampleShippingAddress());

        assertThat(order.getId()).isEqualTo(ORDER_ID);
        assertThat(order.getUserId()).isEqualTo(USER_ID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getShippingAddress()).isEqualTo(sampleShippingAddress());
        assertThat(order.lineCount()).isEqualTo(1);
        assertThat(order.totalQuantity()).isEqualTo(2);
        assertThat(order.getSubtotalAmount()).isEqualTo(new Money(new BigDecimal("1998.00")));
        assertThat(order.getShippingAmount()).isEqualTo(Money.ZERO);
        assertThat(order.getTotalAmount()).isEqualTo(new Money(new BigDecimal("1998.00")));
    }

    @Test
    @DisplayName("placePendingPayment: PENDING_PAYMENT 주문을 생성한다")
    void placePendingPayment_createsPendingPaymentOrder() {
        OrderLine line = sampleOrderLine(new OrderLineId(UUID.randomUUID()), 1);

        Order order = Order.placePendingPayment(ORDER_ID, USER_ID, List.of(line), sampleShippingAddress());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("place: 지정한 상태로 주문을 생성한다")
    void place_createsOrderWithGivenStatus() {
        OrderLine line = sampleOrderLine(new OrderLineId(UUID.randomUUID()), 1);

        Order order = Order.place(
                ORDER_ID,
                USER_ID,
                List.of(line),
                sampleShippingAddress(),
                OrderStatus.PENDING_PAYMENT
        );

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("place: 라인이 없으면 예외")
    void place_emptyLines_throws() {
        assertThatThrownBy(() -> Order.placeConfirmed(
                ORDER_ID,
                USER_ID,
                List.of(),
                sampleShippingAddress()
        ))
                .isInstanceOf(OrderDomainException.class)
                .hasMessageContaining("at least one line");
    }

    @Test
    @DisplayName("place: 배송지가 null이면 예외")
    void place_nullShippingAddress_throws() {
        OrderLine line = sampleOrderLine(new OrderLineId(UUID.randomUUID()), 1);

        assertThatThrownBy(() -> Order.placeConfirmed(ORDER_ID, USER_ID, List.of(line), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("place: 여러 라인의 소계를 합산한다")
    void place_sumsMultipleLineAmounts() {
        OrderLine first = sampleOrderLine(new OrderLineId(UUID.randomUUID()), 1);
        OrderLine second = OrderLine.reconstitute(
                new OrderLineId(UUID.randomUUID()),
                PRODUCT_ID,
                new ProductVariantId(UUID.randomUUID()),
                sampleSnapshot("50.00"),
                3
        );

        Order order = Order.placeConfirmed(
                ORDER_ID,
                USER_ID,
                List.of(first, second),
                sampleShippingAddress()
        );

        assertThat(order.lineCount()).isEqualTo(2);
        assertThat(order.totalQuantity()).isEqualTo(4);
        assertThat(order.getSubtotalAmount()).isEqualTo(new Money(new BigDecimal("1149.00")));
        assertThat(order.getTotalAmount()).isEqualTo(new Money(new BigDecimal("1149.00")));
    }

    private static OrderLine sampleOrderLine(OrderLineId lineId, int quantity) {
        return OrderLine.reconstitute(
                lineId,
                PRODUCT_ID,
                VARIANT_ID,
                sampleSnapshot("999.00"),
                quantity
        );
    }

    private static ShippingAddress sampleShippingAddress() {
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

    private static CartItemSnapshot sampleSnapshot(String price) {
        return new CartItemSnapshot(
                "iPhone 15 Pro",
                "Apple",
                "SKU-001",
                "https://cdn.example.com/image.jpg",
                new Money(new BigDecimal(price)),
                List.of(new CartItemOptionLine(
                        1,
                        UUID.randomUUID(),
                        "Color",
                        UUID.randomUUID(),
                        "Black"
                ))
        );
    }
}
