package com.project.young.orderservice.dataaccess.mapper;

import com.project.young.orderservice.dataaccess.cache.GuestCartDocument;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.valueobject.CartOwnerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.CART_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.domainCartWithOneItem;
import static org.assertj.core.api.Assertions.assertThat;

class GuestCartDocumentMapperTest {

    private final GuestCartDocumentMapper mapper = new GuestCartDocumentMapper();

    @Test
    @DisplayName("toDocument/toCart: 게스트 카트를 JSON 문서와 왕복 변환한다")
    void roundTrip_guestCart() {
        Instant persistedAt = Instant.parse("2026-06-01T12:00:00Z");
        Cart cart = domainCartWithOneItem();

        GuestCartDocument document = mapper.toDocument(cart, persistedAt);
        Cart restored = mapper.toCart(document);

        assertThat(document.getId()).isEqualTo(CART_ID.getValue());
        assertThat(document.getItems()).hasSize(1);
        assertThat(restored.getId()).isEqualTo(CART_ID);
        assertThat(restored.getOwnerType()).isEqualTo(CartOwnerType.GUEST);
        assertThat(restored.getUserId()).isNull();
        assertThat(restored.getItems()).hasSize(1);
        assertThat(restored.getItems().get(0).getSnapshot().productName())
                .isEqualTo(cart.getItems().get(0).getSnapshot().productName());
    }

    @Test
    @DisplayName("toCart: createForGuest 카트를 문서로 저장 후 복원한다")
    void roundTrip_emptyGuestCart() {
        Cart cart = Cart.createForGuest(CART_ID);
        Instant persistedAt = Instant.parse("2026-06-01T12:00:00Z");

        GuestCartDocument document = mapper.toDocument(cart, persistedAt);
        Cart restored = mapper.toCart(document);

        assertThat(restored.getId()).isEqualTo(CART_ID);
        assertThat(restored.isGuestCart()).isTrue();
        assertThat(restored.isEmpty()).isTrue();
        assertThat(document.getCreatedAt()).isEqualTo(persistedAt);
    }
}
