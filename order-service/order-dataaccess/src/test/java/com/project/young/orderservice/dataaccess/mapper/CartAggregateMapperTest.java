package com.project.young.orderservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.dataaccess.entity.CartEntity;
import com.project.young.orderservice.dataaccess.entity.CartItemEntity;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.CartItem;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.CartItemId;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.CART_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.ITEM_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.PRODUCT_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.USER_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.VARIANT_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.persistedCartEntity;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.persistedItemEntity;
import static org.assertj.core.api.Assertions.assertThat;

class CartAggregateMapperTest {

  private final CartAggregateMapper mapper = new CartAggregateMapper();

  @Nested
  @DisplayName("Entity -> Domain")
  class EntityToDomain {

    @Test
    @DisplayName("toCart: 카트와 하위 아이템을 도메인으로 매핑한다")
    void toCart_mapsCartAndItems() {
      Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
      Instant updatedAt = Instant.parse("2026-01-02T00:00:00Z");

      CartItemEntity itemEntity = persistedItemEntity(
          ITEM_ID.getValue(),
          PRODUCT_ID.getValue(),
          VARIANT_ID.getValue(),
          "iPhone 15 Pro",
          new BigDecimal("999.00"),
          2
      );
      CartEntity cartEntity = persistedCartEntity(CART_ID.getValue(), USER_ID.value(), List.of(itemEntity));
      cartEntity.setCreatedAt(createdAt);
      cartEntity.setUpdatedAt(updatedAt);

      Cart cart = mapper.toCart(cartEntity);

      assertThat(cart.getId()).isEqualTo(new CartId(CART_ID.getValue()));
      assertThat(cart.getUserId()).isEqualTo(new UserId(USER_ID.value()));
      assertThat(cart.getCreatedAt()).isEqualTo(createdAt);
      assertThat(cart.getUpdatedAt()).isEqualTo(updatedAt);
      assertThat(cart.getItems()).hasSize(1);

      CartItem item = cart.getItems().get(0);
      assertThat(item.getId()).isEqualTo(new CartItemId(ITEM_ID.getValue()));
      assertThat(item.getProductId()).isEqualTo(new ProductId(PRODUCT_ID.getValue()));
      assertThat(item.getProductVariantId()).isEqualTo(new ProductVariantId(VARIANT_ID.getValue()));
      assertThat(item.getQuantity()).isEqualTo(2);
      assertThat(item.getSnapshot().productName()).isEqualTo("iPhone 15 Pro");
      assertThat(item.getSnapshot().unitPrice()).isEqualTo(new Money(new BigDecimal("999.00")));
      assertThat(item.getSnapshot().variantOptions()).hasSize(1);
      assertThat(item.getSnapshot().variantOptions().get(0).optionGroupName()).isEqualTo("Color");
    }

    @Test
    @DisplayName("toCart: null 엔티티는 null을 반환한다")
    void toCart_nullEntity_returnsNull() {
      assertThat(mapper.toCart(null)).isNull();
    }

    @Test
    @DisplayName("toSnapshot: variant option JSON을 도메인 옵션 라인으로 매핑한다")
    void toSnapshot_mapsOptionLines() {
      CartItemEntity itemEntity = persistedItemEntity(
          ITEM_ID.getValue(),
          PRODUCT_ID.getValue(),
          VARIANT_ID.getValue(),
          "iPhone 15 Pro",
          new BigDecimal("999.00"),
          1
      );

      var snapshot = mapper.toSnapshot(itemEntity);

      assertThat(snapshot.brand()).isEqualTo("Apple");
      assertThat(snapshot.sku()).isEqualTo("SKU-001");
      assertThat(snapshot.variantOptions()).hasSize(1);
      assertThat(snapshot.variantOptions().get(0).optionValueName()).isEqualTo("Black");
    }
  }
}
