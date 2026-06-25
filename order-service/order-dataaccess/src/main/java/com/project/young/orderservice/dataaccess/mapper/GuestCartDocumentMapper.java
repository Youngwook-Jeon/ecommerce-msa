package com.project.young.orderservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.dataaccess.cache.GuestCartDocument;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.CartItem;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.CartItemId;
import com.project.young.orderservice.domain.valueobject.CartItemOptionLine;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.CartOwnerType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class GuestCartDocumentMapper {

    public GuestCartDocument toDocument(Cart cart, Instant persistedAt) {
        Instant createdAt = cart.getCreatedAt() != null ? cart.getCreatedAt() : persistedAt;
        Instant updatedAt = cart.getUpdatedAt() != null ? cart.getUpdatedAt() : persistedAt;

        List<GuestCartDocument.GuestCartItemDocument> itemDocuments = new ArrayList<>(cart.getItems().size());
        for (CartItem item : cart.getItems()) {
            itemDocuments.add(toItemDocument(item, createdAt, updatedAt));
        }

        return GuestCartDocument.builder()
                .id(cart.getId().getValue())
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .items(itemDocuments)
                .build();
    }

    public Cart toCart(GuestCartDocument document) {
        List<CartItem> items = document.getItems().stream()
                .map(this::toCartItem)
                .toList();

        return Cart.builder()
                .cartId(new CartId(document.getId()))
                .ownerType(CartOwnerType.GUEST)
                .items(items)
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    private GuestCartDocument.GuestCartItemDocument toItemDocument(
            CartItem item,
            Instant defaultCreatedAt,
            Instant defaultUpdatedAt
    ) {
        CartItemSnapshot snapshot = item.getSnapshot();
        return GuestCartDocument.GuestCartItemDocument.builder()
                .id(item.getId().getValue())
                .productId(item.getProductId().getValue())
                .productVariantId(item.getProductVariantId().getValue())
                .productName(snapshot.productName())
                .brand(snapshot.brand())
                .sku(snapshot.sku())
                .imageUrl(snapshot.imageUrl())
                .unitPrice(snapshot.unitPrice().getAmount())
                .variantOptions(toOptionDocuments(snapshot.variantOptions()))
                .quantity(item.getQuantity())
                .createdAt(defaultCreatedAt)
                .updatedAt(defaultUpdatedAt)
                .build();
    }

    private CartItem toCartItem(GuestCartDocument.GuestCartItemDocument document) {
        return CartItem.reconstitute(
                new CartItemId(document.getId()),
                new ProductId(document.getProductId()),
                new ProductVariantId(document.getProductVariantId()),
                new CartItemSnapshot(
                        document.getProductName(),
                        document.getBrand(),
                        document.getSku(),
                        document.getImageUrl(),
                        new Money(document.getUnitPrice()),
                        toOptionLines(document.getVariantOptions())
                ),
                document.getQuantity()
        );
    }

    private List<GuestCartDocument.GuestCartOptionLineDocument> toOptionDocuments(List<CartItemOptionLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<GuestCartDocument.GuestCartOptionLineDocument> documents = new ArrayList<>(lines.size());
        for (CartItemOptionLine line : lines) {
            documents.add(GuestCartDocument.GuestCartOptionLineDocument.builder()
                    .stepOrder(line.stepOrder())
                    .productOptionGroupId(line.productOptionGroupId())
                    .optionGroupName(line.optionGroupName())
                    .productOptionValueId(line.productOptionValueId())
                    .optionValueName(line.optionValueName())
                    .build());
        }
        return documents;
    }

    private List<CartItemOptionLine> toOptionLines(List<GuestCartDocument.GuestCartOptionLineDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return documents.stream()
                .map(document -> new CartItemOptionLine(
                        document.getStepOrder(),
                        document.getProductOptionGroupId(),
                        document.getOptionGroupName(),
                        document.getProductOptionValueId(),
                        document.getOptionValueName()
                ))
                .toList();
    }
}
