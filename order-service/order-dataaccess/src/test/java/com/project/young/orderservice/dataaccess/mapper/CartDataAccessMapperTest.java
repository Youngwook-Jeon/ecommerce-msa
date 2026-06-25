package com.project.young.orderservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.dataaccess.entity.CartEntity;
import com.project.young.orderservice.dataaccess.entity.CartItemEntity;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.valueobject.CartItemId;
import com.project.young.orderservice.domain.valueobject.CartOwnerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.CART_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.ITEM_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.PRODUCT_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.USER_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.VARIANT_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.domainCartWithOneItem;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.domainItem;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.persistedCartEntity;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.persistedItemEntity;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.sampleSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

class CartDataAccessMapperTest {

  private final CartDataAccessMapper mapper = new CartDataAccessMapper();

  @Nested
  @DisplayName("Domain -> Entity")
  class DomainToEntity {

    @Test
    @DisplayName("cartToCartEntity: 카트와 하위 아이템을 엔티티로 매핑한다")
    void cartToCartEntity_mapsCartAndItems() {
      Cart cart = domainCartWithOneItem();

      CartEntity entity = mapper.cartToCartEntity(cart);

      assertThat(entity.getId()).isEqualTo(CART_ID.getValue());
      assertThat(entity.getUserId()).isEqualTo(USER_ID.value());
      assertThat(entity.getItems()).hasSize(1);

      CartItemEntity itemEntity = entity.getItems().get(0);
      assertThat(itemEntity.getCart()).isSameAs(entity);
      assertThat(itemEntity.getId()).isEqualTo(ITEM_ID.getValue());
      assertThat(itemEntity.getProductId()).isEqualTo(PRODUCT_ID.getValue());
      assertThat(itemEntity.getProductVariantId()).isEqualTo(VARIANT_ID.getValue());
      assertThat(itemEntity.getQuantity()).isEqualTo(2);
      assertThat(itemEntity.getProductName()).isEqualTo("iPhone 15 Pro");
      assertThat(itemEntity.getUnitPrice()).isEqualByComparingTo("999.00");
      assertThat(itemEntity.getVariantOptionsJson()).hasSize(1);
      assertThat(itemEntity.getVariantOptionsJson().get(0).getOptionGroupName()).isEqualTo("Color");
    }
  }

  @Nested
  @DisplayName("Merge Update")
  class MergeUpdate {

    @Test
    @DisplayName("updateEntityFromDomain: 기존 variant는 엔티티를 재사용하고 필드만 갱신한다")
    void updateEntityFromDomain_reusesExistingItemEntity() {
      CartItemEntity existingItem = persistedItemEntity(
          ITEM_ID.getValue(),
          PRODUCT_ID.getValue(),
          VARIANT_ID.getValue(),
          "Old Name",
          new BigDecimal("100.00"),
          1
      );
      CartEntity current = persistedCartEntity(CART_ID.getValue(), USER_ID.value(), List.of(existingItem));

      Cart updatedCart = Cart.builder()
          .cartId(CART_ID)
          .ownerType(CartOwnerType.USER)
          .userId(USER_ID)
          .items(List.of(domainItem(
              ITEM_ID,
              PRODUCT_ID,
              VARIANT_ID,
              sampleSnapshot("1099.00"),
              3
          )))
          .build();

      mapper.updateEntityFromDomain(updatedCart, current);

      assertThat(current.getItems()).hasSize(1);
      assertThat(current.getItems().get(0)).isSameAs(existingItem);
      assertThat(existingItem.getProductName()).isEqualTo("iPhone 15 Pro");
      assertThat(existingItem.getUnitPrice()).isEqualByComparingTo("1099.00");
      assertThat(existingItem.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("updateEntityFromDomain: 새 variant는 아이템을 추가한다")
    void updateEntityFromDomain_addsNewItem() {
      UUID newVariantId = UUID.fromString("018f0000-0000-7000-8000-000000000202");
      CartItemId newItemId = new CartItemId(UUID.fromString("018f0000-0000-7000-8000-000000000302"));

      CartEntity current = persistedCartEntity(
          CART_ID.getValue(),
          USER_ID.value(),
          List.of(persistedItemEntity(
              ITEM_ID.getValue(),
              PRODUCT_ID.getValue(),
              VARIANT_ID.getValue(),
              "Keep Me",
              new BigDecimal("100.00"),
              1
          ))
      );

      Cart updatedCart = Cart.builder()
          .cartId(CART_ID)
          .ownerType(CartOwnerType.USER)
          .userId(USER_ID)
          .items(List.of(
              domainItem(ITEM_ID, PRODUCT_ID, VARIANT_ID, sampleSnapshot("100.00"), 1),
              domainItem(
                  newItemId,
                  PRODUCT_ID,
                  new ProductVariantId(newVariantId),
                  sampleSnapshot("200.00"),
                  2
              )
          ))
          .build();

      mapper.updateEntityFromDomain(updatedCart, current);

      assertThat(current.getItems()).hasSize(2);
      assertThat(current.getItems())
          .extracting(CartItemEntity::getProductVariantId)
          .containsExactlyInAnyOrder(VARIANT_ID.getValue(), newVariantId);
    }

    @Test
    @DisplayName("updateEntityFromDomain: 도메인에 없는 variant는 컬렉션에서 제거한다")
    void updateEntityFromDomain_removesOrphanItemsFromCollection() {
      UUID removedVariantId = UUID.fromString("018f0000-0000-7000-8000-000000000299");
      CartItemEntity keepItem = persistedItemEntity(
          ITEM_ID.getValue(),
          PRODUCT_ID.getValue(),
          VARIANT_ID.getValue(),
          "Keep Me",
          new BigDecimal("100.00"),
          1
      );
      CartItemEntity removeItem = persistedItemEntity(
          UUID.fromString("018f0000-0000-7000-8000-000000000399"),
          PRODUCT_ID.getValue(),
          removedVariantId,
          "Remove Me",
          new BigDecimal("50.00"),
          1
      );
      CartEntity current = persistedCartEntity(
          CART_ID.getValue(),
          USER_ID.value(),
          List.of(keepItem, removeItem)
      );

      Cart updatedCart = domainCartWithOneItem();

      mapper.updateEntityFromDomain(updatedCart, current);

      assertThat(current.getItems()).hasSize(1);
      assertThat(current.getItems().get(0)).isSameAs(keepItem);
      assertThat(current.getItems().get(0).getProductVariantId()).isEqualTo(VARIANT_ID.getValue());
    }
  }
}
