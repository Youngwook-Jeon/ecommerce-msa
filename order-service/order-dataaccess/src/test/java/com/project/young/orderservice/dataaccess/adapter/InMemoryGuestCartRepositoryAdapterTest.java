package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.exception.CartNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.CART_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.ITEM_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.PRODUCT_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.VARIANT_ID;
import static com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.sampleSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryGuestCartRepositoryAdapterTest {

    private InMemoryGuestCartRepositoryAdapter repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryGuestCartRepositoryAdapter();
    }

    @Test
    @DisplayName("insert/findById: 게스트 카트를 저장하고 조회한다")
    void insertAndFind_success() {
        Cart cart = guestCartWithOneItem();

        repository.insert(cart);

        assertThat(repository.findById(CART_ID)).contains(cart);
    }

    @Test
    @DisplayName("update: 기존 게스트 카트를 갱신한다")
    void update_success() {
        Cart cart = guestCartWithOneItem();
        repository.insert(cart);

        Cart updated = Cart.createForGuest(CART_ID);
        updated.addOrMergeItem(
                PRODUCT_ID,
                VARIANT_ID,
                sampleSnapshot("120.00"),
                3,
                ITEM_ID
        );

        repository.update(updated);

        assertThat(repository.findById(CART_ID))
                .get()
                .extracting(found -> found.getItems().get(0).getQuantity())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("delete: 게스트 카트를 삭제한다")
    void delete_success() {
        repository.insert(guestCartWithOneItem());

        repository.delete(CART_ID);

        assertThat(repository.findById(CART_ID)).isEmpty();
    }

    @Test
    @DisplayName("insert: 사용자 카트는 거부한다")
    void insert_userCart_throws() {
        assertThatThrownBy(() -> repository.insert(
                com.project.young.orderservice.dataaccess.support.CartMapperTestFixtures.domainCartWithOneItem()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("update: 없는 카트는 CartNotFoundException")
    void update_missingCart_throws() {
        assertThatThrownBy(() -> repository.update(guestCartWithOneItem()))
                .isInstanceOf(CartNotFoundException.class);
    }

    private Cart guestCartWithOneItem() {
        Cart cart = Cart.createForGuest(CART_ID);
        cart.addOrMergeItem(
                PRODUCT_ID,
                VARIANT_ID,
                sampleSnapshot("100.00"),
                1,
                ITEM_ID
        );
        return cart;
    }
}
