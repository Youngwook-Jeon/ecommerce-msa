package com.project.young.orderservice.dataaccess.mapper;

import com.project.young.orderservice.dataaccess.entity.CartEntity;
import com.project.young.orderservice.dataaccess.entity.CartItemEntity;
import com.project.young.orderservice.dataaccess.entity.CartItemOptionLineJson;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.CartItem;
import com.project.young.orderservice.domain.valueobject.CartItemOptionLine;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class CartDataAccessMapper {

    public CartEntity cartToCartEntity(Cart cart) {
        CartEntity entity = CartEntity.builder()
                .id(cart.getId().getValue())
                .userId(cart.getUserId().value())
                .build();

        for (CartItem item : cart.getItems()) {
            CartItemEntity itemEntity = new CartItemEntity();
            itemEntity.setCart(entity);
            updateItemEntityFromDomain(item, itemEntity);
            entity.addItem(itemEntity);
        }

        return entity;
    }

    public void updateEntityFromDomain(Cart cart, CartEntity current) {
        Map<UUID, CartItemEntity> existingByVariantId = new HashMap<>();
        for (CartItemEntity existing : current.getItems()) {
            existingByVariantId.put(existing.getProductVariantId(), existing);
        }

        Set<UUID> retainedVariantIds = new HashSet<>();
        for (CartItem item : cart.getItems()) {
            UUID variantId = item.getProductVariantId().getValue();
            retainedVariantIds.add(variantId);

            CartItemEntity itemEntity = existingByVariantId.get(variantId);
            if (itemEntity == null) {
                itemEntity = new CartItemEntity();
                itemEntity.setCart(current);
                current.addItem(itemEntity);
            }

            updateItemEntityFromDomain(item, itemEntity);
        }

        current.getItems().removeIf(item -> !retainedVariantIds.contains(item.getProductVariantId()));
    }

    public void updateItemEntityFromDomain(CartItem item, CartItemEntity entity) {
        entity.setId(item.getId().getValue());
        entity.setProductId(item.getProductId().getValue());
        entity.setProductVariantId(item.getProductVariantId().getValue());
        applySnapshot(entity, item.getSnapshot());
        entity.setQuantity(item.getQuantity());
    }

    private void applySnapshot(CartItemEntity entity, CartItemSnapshot snapshot) {
        entity.setProductName(snapshot.productName());
        entity.setBrand(snapshot.brand());
        entity.setSku(snapshot.sku());
        entity.setImageUrl(snapshot.imageUrl());
        entity.setUnitPrice(snapshot.unitPrice().getAmount());
        List<CartItemOptionLineJson> newJsonLines = toJsonLines(snapshot.variantOptions());
        if (!newJsonLines.equals(entity.getVariantOptionsJson())) {
            entity.setVariantOptionsJson(newJsonLines);
        }
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
}
