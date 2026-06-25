package com.project.young.orderservice.dataaccess.mapper;

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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class CartAggregateMapper {

    public Cart toCart(CartEntity entity) {
        if (entity == null) {
            return null;
        }

        List<CartItem> items = entity.getItems().stream()
                .map(this::toCartItem)
                .toList();

        return Cart.builder()
                .cartId(new CartId(entity.getId()))
                .ownerType(CartOwnerType.USER)
                .userId(new UserId(entity.getUserId()))
                .items(items)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public CartItem toCartItem(CartItemEntity entity) {
        return CartItem.reconstitute(
                new CartItemId(entity.getId()),
                new ProductId(entity.getProductId()),
                new ProductVariantId(entity.getProductVariantId()),
                toSnapshot(entity),
                entity.getQuantity()
        );
    }

    public CartItemSnapshot toSnapshot(CartItemEntity entity) {
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
}
