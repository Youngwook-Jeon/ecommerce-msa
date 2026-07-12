package com.project.young.orderservice.domain.entity;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.CartItemId;
import com.project.young.orderservice.domain.valueobject.CartItemOptionLine;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.OrderLineId;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderLineTest {

    private static final UserId USER_ID = new UserId("keycloak-sub-order-line-1");
    private static final CartId CART_ID = new CartId(UUID.randomUUID());
    private static final ProductId PRODUCT_ID = new ProductId(UUID.randomUUID());
    private static final ProductVariantId VARIANT_ID = new ProductVariantId(UUID.randomUUID());
    private static final OrderLineId LINE_ID = new OrderLineId(UUID.randomUUID());

    @Test
    @DisplayName("fromCartItem: 카트 라인 스냅샷을 주문 라인으로 복사한다")
    void fromCartItem_copiesCartItemSnapshot() {
        Cart cart = Cart.createForUser(USER_ID, CART_ID);
        CartItem cartItem = cart.addOrMergeItem(
                PRODUCT_ID,
                VARIANT_ID,
                sampleSnapshot("999.00"),
                2,
                new CartItemId(UUID.randomUUID())
        );

        OrderLine orderLine = OrderLine.fromCartItem(cartItem, LINE_ID);

        assertThat(orderLine.getId()).isEqualTo(LINE_ID);
        assertThat(orderLine.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(orderLine.getProductVariantId()).isEqualTo(VARIANT_ID);
        assertThat(orderLine.getQuantity()).isEqualTo(2);
        assertThat(orderLine.getSnapshot()).isEqualTo(cartItem.getSnapshot());
        assertThat(orderLine.lineAmount()).isEqualTo(new Money(new BigDecimal("1998.00")));
    }

    @Test
    @DisplayName("reconstitute: 양수 수량이 아니면 예외")
    void reconstitute_nonPositiveQuantity_throws() {
        assertThatThrownBy(() -> OrderLine.reconstitute(
                LINE_ID,
                PRODUCT_ID,
                VARIANT_ID,
                sampleSnapshot("100.00"),
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be positive");
    }

    @Test
    @DisplayName("fromCartItem: cartItem이 null이면 예외")
    void fromCartItem_nullCartItem_throws() {
        assertThatThrownBy(() -> OrderLine.fromCartItem(null, LINE_ID))
                .isInstanceOf(NullPointerException.class);
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
