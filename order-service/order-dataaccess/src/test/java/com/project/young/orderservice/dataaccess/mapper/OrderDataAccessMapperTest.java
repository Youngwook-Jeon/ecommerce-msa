package com.project.young.orderservice.dataaccess.mapper;

import com.project.young.orderservice.dataaccess.entity.OrderEntity;
import com.project.young.orderservice.dataaccess.entity.OrderLineEntity;
import com.project.young.orderservice.dataaccess.enums.OrderStatusEntity;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.valueobject.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.project.young.orderservice.dataaccess.support.OrderMapperTestFixtures.LINE_ID;
import static com.project.young.orderservice.dataaccess.support.OrderMapperTestFixtures.ORDER_ID;
import static com.project.young.orderservice.dataaccess.support.OrderMapperTestFixtures.USER_ID;
import static com.project.young.orderservice.dataaccess.support.OrderMapperTestFixtures.domainOrderWithOneLine;
import static org.assertj.core.api.Assertions.assertThat;

class OrderDataAccessMapperTest {

    private final OrderDataAccessMapper mapper = new OrderDataAccessMapper();

    @Nested
    @DisplayName("Domain -> Entity")
    class DomainToEntity {

        @Test
        @DisplayName("orderToOrderEntity: 주문과 하위 라인을 엔티티로 매핑한다")
        void orderToOrderEntity_mapsOrderAndLines() {
            Order order = domainOrderWithOneLine();

            OrderEntity entity = mapper.orderToOrderEntity(order);

            assertThat(entity.getId()).isEqualTo(ORDER_ID.getValue());
            assertThat(entity.getUserId()).isEqualTo(USER_ID.value());
            assertThat(entity.getStatus()).isEqualTo(OrderStatusEntity.CONFIRMED);
            assertThat(entity.getSubtotalAmount()).isEqualByComparingTo("1998.00");
            assertThat(entity.getShippingAmount()).isEqualByComparingTo("0.00");
            assertThat(entity.getTotalAmount()).isEqualByComparingTo("1998.00");
            assertThat(entity.getShippingRecipientName()).isEqualTo("Kim Young");
            assertThat(entity.getShippingPhone()).isEqualTo("01012345678");
            assertThat(entity.getShippingAddressLine1()).isEqualTo("123 Main St");
            assertThat(entity.getShippingAddressLine2()).isEqualTo("Apt 4B");
            assertThat(entity.getShippingCity()).isEqualTo("Seoul");
            assertThat(entity.getShippingPostalCode()).isEqualTo("04524");
            assertThat(entity.getShippingCountryCode()).isEqualTo("KR");
            assertThat(entity.getLines()).hasSize(1);

            OrderLineEntity lineEntity = entity.getLines().get(0);
            assertThat(lineEntity.getOrder()).isSameAs(entity);
            assertThat(lineEntity.getId()).isEqualTo(LINE_ID.getValue());
            assertThat(lineEntity.getProductName()).isEqualTo("iPhone 15 Pro");
            assertThat(lineEntity.getUnitPrice()).isEqualByComparingTo("999.00");
            assertThat(lineEntity.getQuantity()).isEqualTo(2);
            assertThat(lineEntity.getVariantOptionsJson()).hasSize(1);
            assertThat(lineEntity.getVariantOptionsJson().get(0).getOptionGroupName()).isEqualTo("Color");
        }
    }

    @Nested
    @DisplayName("Status 변환")
    class StatusConversion {

        @Test
        @DisplayName("toEntityStatus / toDomainStatus: 도메인과 엔티티 상태를 상호 변환한다")
        void statusConverters_roundTrip() {
            assertThat(mapper.toEntityStatus(OrderStatus.CONFIRMED)).isEqualTo(OrderStatusEntity.CONFIRMED);
            assertThat(mapper.toEntityStatus(OrderStatus.PENDING_PAYMENT)).isEqualTo(OrderStatusEntity.PENDING_PAYMENT);
            assertThat(mapper.toDomainStatus(OrderStatusEntity.CANCELLED)).isEqualTo(OrderStatus.CANCELLED);
            assertThat(mapper.toDomainStatus(OrderStatusEntity.EXPIRED)).isEqualTo(OrderStatus.EXPIRED);
        }
    }
}
