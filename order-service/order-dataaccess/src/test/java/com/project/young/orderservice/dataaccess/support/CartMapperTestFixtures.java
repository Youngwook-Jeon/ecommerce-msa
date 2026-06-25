package com.project.young.orderservice.dataaccess.support;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.dataaccess.entity.CartEntity;
import com.project.young.orderservice.dataaccess.entity.CartItemEntity;
import com.project.young.orderservice.dataaccess.entity.CartItemOptionLineJson;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.CartItem;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.CartItemId;
import com.project.young.orderservice.domain.valueobject.CartItemOptionLine;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.CartOwnerType;
import com.project.young.orderservice.domain.valueobject.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CartMapperTestFixtures {

  public static final UserId USER_ID = new UserId("keycloak-sub-cart-test");
  public static final CartId CART_ID = new CartId(UUID.fromString("018f0000-0000-7000-8000-000000000001"));
  public static final ProductId PRODUCT_ID = new ProductId(UUID.fromString("018f0000-0000-7000-8000-000000000101"));
  public static final ProductVariantId VARIANT_ID = new ProductVariantId(UUID.fromString("018f0000-0000-7000-8000-000000000201"));
  public static final CartItemId ITEM_ID = new CartItemId(UUID.fromString("018f0000-0000-7000-8000-000000000301"));

  private CartMapperTestFixtures() {
  }

  public static CartItemSnapshot sampleSnapshot(String price) {
    return new CartItemSnapshot(
        "iPhone 15 Pro",
        "Apple",
        "SKU-001",
        "https://cdn.example.com/image.jpg",
        new Money(new BigDecimal(price)),
        List.of(new CartItemOptionLine(
            1,
            UUID.fromString("018f0000-0000-7000-8000-000000000401"),
            "Color",
            UUID.fromString("018f0000-0000-7000-8000-000000000501"),
            "Black"
        ))
    );
  }

  public static Cart domainCartWithOneItem() {
    return Cart.builder()
        .cartId(CART_ID)
        .ownerType(CartOwnerType.USER)
        .userId(USER_ID)
        .items(List.of(domainItem(ITEM_ID, PRODUCT_ID, VARIANT_ID, sampleSnapshot("999.00"), 2)))
        .build();
  }

  public static CartItem domainItem(
      CartItemId itemId,
      ProductId productId,
      ProductVariantId variantId,
      CartItemSnapshot snapshot,
      int quantity
  ) {
    return CartItem.reconstitute(itemId, productId, variantId, snapshot, quantity);
  }

  public static CartEntity persistedCartEntity(UUID cartId, String userId, List<CartItemEntity> items) {
    CartEntity cart = CartEntity.builder()
        .id(cartId)
        .userId(userId)
        .build();
    for (CartItemEntity item : items) {
      cart.addItem(item);
    }
    return cart;
  }

  public static CartItemEntity persistedItemEntity(
      UUID itemId,
      UUID productId,
      UUID variantId,
      String productName,
      BigDecimal unitPrice,
      int quantity
  ) {
    return CartItemEntity.builder()
        .id(itemId)
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
