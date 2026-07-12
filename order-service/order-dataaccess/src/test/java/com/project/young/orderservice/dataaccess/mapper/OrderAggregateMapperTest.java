package com.project.young.orderservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.dataaccess.entity.OrderEntity;
import com.project.young.orderservice.dataaccess.entity.OrderLineEntity;
import com.project.young.orderservice.dataaccess.enums.OrderStatusEntity;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.entity.OrderLine;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.OrderLineId;
import com.project.young.orderservice.domain.valueobject.OrderStatus;
import com.project.young.orderservice.domain.valueobject.ShippingAddress;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.PRODUCT_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.VARIANT_ID;
import static com.project.young.orderservice.dataaccess.support.OrderMapperTestFixtures.LINE_ID;
import static com.project.young.orderservice.dataaccess.support.OrderMapperTestFixtures.ORDER_ID;
import static com.project.young.orderservice.dataaccess.support.OrderMapperTestFixtures.USER_ID;
import static com.project.young.orderservice.dataaccess.support.OrderMapperTestFixtures.persistedLineEntity;
import static com.project.young.orderservice.dataaccess.support.OrderMapperTestFixtures.persistedOrderEntity;
import static org.assertj.core.api.Assertions.assertThat;

class OrderAggregateMapperTest {

    private final OrderAggregateMapper mapper = new OrderAggregateMapper();

    @Nested
    @DisplayName("Entity -> Domain")
    class EntityToDomain {

        @Test
        @DisplayName("toOrder: 주문과 하위 라인을 도메인으로 매핑한다")
        void toOrder_mapsOrderAndLines() {
            Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
            Instant updatedAt = Instant.parse("2026-01-02T00:00:00Z");

            OrderLineEntity lineEntity = persistedLineEntity(
                    LINE_ID.getValue(),
                    PRODUCT_ID.getValue(),
                    VARIANT_ID.getValue(),
                    "iPhone 15 Pro",
                    new BigDecimal("999.00"),
                    2
            );
            OrderEntity orderEntity = persistedOrderEntity(
                    ORDER_ID.getValue(),
                    USER_ID.value(),
                    List.of(lineEntity)
            );
            orderEntity.setCreatedAt(createdAt);
            orderEntity.setUpdatedAt(updatedAt);

            Order order = mapper.toOrder(orderEntity);

            assertThat(order.getId()).isEqualTo(new OrderId(ORDER_ID.getValue()));
            assertThat(order.getUserId()).isEqualTo(new UserId(USER_ID.value()));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getCreatedAt()).isEqualTo(createdAt);
            assertThat(order.getUpdatedAt()).isEqualTo(updatedAt);
            assertThat(order.getSubtotalAmount()).isEqualTo(new Money(new BigDecimal("1998.00")));
            assertThat(order.getShippingAmount()).isEqualTo(Money.ZERO);
            assertThat(order.getTotalAmount()).isEqualTo(new Money(new BigDecimal("1998.00")));
            assertThat(order.getLines()).hasSize(1);

            OrderLine line = order.getLines().get(0);
            assertThat(line.getId()).isEqualTo(new OrderLineId(LINE_ID.getValue()));
            assertThat(line.getProductId()).isEqualTo(new ProductId(PRODUCT_ID.getValue()));
            assertThat(line.getProductVariantId()).isEqualTo(new ProductVariantId(VARIANT_ID.getValue()));
            assertThat(line.getQuantity()).isEqualTo(2);
            assertThat(line.getSnapshot().productName()).isEqualTo("iPhone 15 Pro");
            assertThat(line.getSnapshot().unitPrice()).isEqualTo(new Money(new BigDecimal("999.00")));
            assertThat(line.getSnapshot().variantOptions()).hasSize(1);
            assertThat(line.getSnapshot().variantOptions().get(0).optionGroupName()).isEqualTo("Color");
        }

        @Test
        @DisplayName("toOrder: 배송지 스냅샷을 도메인으로 매핑한다")
        void toOrder_mapsShippingAddress() {
            OrderEntity orderEntity = persistedOrderEntity(
                    ORDER_ID.getValue(),
                    USER_ID.value(),
                    List.of(persistedLineEntity(
                            LINE_ID.getValue(),
                            PRODUCT_ID.getValue(),
                            VARIANT_ID.getValue(),
                            "iPhone 15 Pro",
                            new BigDecimal("999.00"),
                            1
                    ))
            );

            ShippingAddress address = mapper.toOrder(orderEntity).getShippingAddress();

            assertThat(address.recipientName()).isEqualTo("Kim Young");
            assertThat(address.phone()).isEqualTo("01012345678");
            assertThat(address.addressLine1()).isEqualTo("123 Main St");
            assertThat(address.addressLine2()).isEqualTo("Apt 4B");
            assertThat(address.city()).isEqualTo("Seoul");
            assertThat(address.postalCode()).isEqualTo("04524");
            assertThat(address.countryCode()).isEqualTo("KR");
        }

        @Test
        @DisplayName("toOrder: null 엔티티는 null을 반환한다")
        void toOrder_nullEntity_returnsNull() {
            assertThat(mapper.toOrder(null)).isNull();
        }
    }
}
