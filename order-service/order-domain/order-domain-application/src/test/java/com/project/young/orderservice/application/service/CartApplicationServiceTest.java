package com.project.young.orderservice.application.service;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.application.port.output.CartCatalogLineKey;
import com.project.young.orderservice.application.port.output.IdGenerator;
import com.project.young.orderservice.application.port.output.ProductCatalogPort;
import com.project.young.orderservice.application.port.output.view.CartCatalogLineView;
import com.project.young.orderservice.application.port.output.view.CartCatalogOptionLineView;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.exception.CartDomainException;
import com.project.young.orderservice.domain.exception.CartNotFoundException;
import com.project.young.orderservice.domain.repository.CartRepository;
import com.project.young.orderservice.domain.repository.GuestCartRepository;
import com.project.young.orderservice.domain.sync.CartSyncChange;
import com.project.young.orderservice.domain.sync.CartSyncResult;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.CartItemId;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartApplicationServiceTest {

    private static final UserId USER_ID = new UserId("user-1");
    private static final CartId CART_ID = new CartId(UUID.randomUUID());
    private static final ProductId PRODUCT_ID = new ProductId(UUID.randomUUID());
    private static final ProductVariantId VARIANT_ID = new ProductVariantId(UUID.randomUUID());

    @Mock
    private CartRepository cartRepository;
    @Mock
    private GuestCartRepository guestCartRepository;
    @Mock
    private ProductCatalogPort productCatalogPort;
    @Mock
    private IdGenerator idGenerator;

    @InjectMocks
    private CartApplicationService cartApplicationService;

    @Test
    @DisplayName("getOrCreateUserCart: 기존 카트가 없으면 insert 후 반환한다")
    void getOrCreateUserCart_createsCart() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(idGenerator.generateId()).thenReturn(CART_ID.getValue());

        Cart cart = cartApplicationService.getOrCreateUserCart(USER_ID);

        assertThat(cart.isUserCart()).isTrue();
        assertThat(cart.getUserId()).isEqualTo(USER_ID);
        verify(cartRepository).insert(cart);
    }

    @Test
    @DisplayName("addUserItem: 카트를 로드한 뒤 라인을 추가하고 update 한다")
    void addUserItem_addsLineAndUpdatesCart() {
        Cart cart = Cart.createForUser(USER_ID, CART_ID);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(idGenerator.generateId()).thenReturn(UUID.randomUUID());
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of(
                VARIANT_ID.getValue(),
                catalogView(true, 5)
        ));

        Cart updated = cartApplicationService.addUserItem(USER_ID, PRODUCT_ID, VARIANT_ID, 2);

        assertThat(updated.itemCount()).isEqualTo(1);
        assertThat(updated.getItems().getFirst().getQuantity()).isEqualTo(2);
        assertThat(updated.getItems().getFirst().getSnapshot().productName()).isEqualTo("Phone");
        verify(cartRepository).update(cart);
        verify(cartRepository, never()).findById(any());
    }

    @Test
    @DisplayName("addUserItem: 신규 사용자는 getOrCreate로 카트를 만든 뒤 추가한다")
    void addUserItem_createsCartWhenMissing() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(idGenerator.generateId()).thenReturn(CART_ID.getValue(), UUID.randomUUID());
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of(
                VARIANT_ID.getValue(),
                catalogView(true, 5)
        ));

        Cart updated = cartApplicationService.addUserItem(USER_ID, PRODUCT_ID, VARIANT_ID, 1);

        assertThat(updated.itemCount()).isEqualTo(1);
        verify(cartRepository).insert(any(Cart.class));
        verify(cartRepository).update(any(Cart.class));
    }

    @Test
    @DisplayName("addUserItem: variant를 찾지 못하면 예외")
    void addUserItem_variantNotFound() {
        Cart cart = Cart.createForUser(USER_ID, CART_ID);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of());

        assertThatThrownBy(() -> cartApplicationService.addUserItem(USER_ID, PRODUCT_ID, VARIANT_ID, 1))
                .isInstanceOf(CartDomainException.class)
                .hasMessageContaining("not found");
        verify(cartRepository, never()).update(any());
    }

    @Test
    @DisplayName("addUserItem: 재고 부족이면 예외")
    void addUserItem_insufficientStock() {
        Cart cart = Cart.createForUser(USER_ID, CART_ID);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of(
                VARIANT_ID.getValue(),
                catalogView(true, 1)
        ));

        assertThatThrownBy(() -> cartApplicationService.addUserItem(USER_ID, PRODUCT_ID, VARIANT_ID, 2))
                .isInstanceOf(CartDomainException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    @DisplayName("updateUserItemQuantity: 카트가 없으면 CartNotFoundException")
    void updateUserItemQuantity_cartNotFound() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartApplicationService.updateUserItemQuantity(
                USER_ID,
                new CartItemId(UUID.randomUUID()),
                2
        )).isInstanceOf(CartNotFoundException.class);
    }

    @Test
    @DisplayName("syncGuestCart: cart item 키로 catalog를 조회하고 reconcile 결과를 저장한다")
    void syncGuestCart_reconcilesAndUpdates() {
        Cart cart = Cart.createForGuest(CART_ID);
        CartItemId itemId = new CartItemId(UUID.randomUUID());
        cart.addOrMergeItem(
                PRODUCT_ID,
                VARIANT_ID,
                new com.project.young.orderservice.domain.valueobject.CartItemSnapshot(
                        "Old",
                        "Brand",
                        "SKU",
                        null,
                        new Money(new BigDecimal("90.00")),
                        List.of()
                ),
                1,
                itemId
        );

        when(guestCartRepository.findById(CART_ID)).thenReturn(Optional.of(cart));
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of(
                VARIANT_ID.getValue(),
                catalogView(true, 3)
        ));

        CartSyncResult result = cartApplicationService.syncGuestCart(CART_ID);

        assertThat(result.changes()).isNotEmpty();
        assertThat(result.changes().getFirst()).isInstanceOf(CartSyncChange.PriceUpdated.class);
        verify(productCatalogPort).resolveLines(any());
        verify(guestCartRepository).update(cart);
    }

    @Test
    @DisplayName("syncGuestCart: 빈 카트는 catalog 호출 없이 빈 변경 목록을 반환한다")
    void syncGuestCart_emptyCart() {
        Cart cart = Cart.createForGuest(CART_ID);
        when(guestCartRepository.findById(CART_ID)).thenReturn(Optional.of(cart));

        CartSyncResult result = cartApplicationService.syncGuestCart(CART_ID);

        assertThat(result.changes()).isEmpty();
        verify(productCatalogPort, never()).resolveLines(any());
        verify(guestCartRepository, never()).update(any());
    }

    @Test
    @DisplayName("syncGuestCart: cart item별 productId를 catalog key에 포함한다")
    void syncGuestCart_passesProductIdInKeys() {
        Cart cart = Cart.createForGuest(CART_ID);
        cart.addOrMergeItem(
                PRODUCT_ID,
                VARIANT_ID,
                new com.project.young.orderservice.domain.valueobject.CartItemSnapshot(
                        "Old",
                        "Brand",
                        "SKU",
                        null,
                        new Money(new BigDecimal("90.00")),
                        List.of()
                ),
                1,
                new CartItemId(UUID.randomUUID())
        );
        when(guestCartRepository.findById(CART_ID)).thenReturn(Optional.of(cart));
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of());

        cartApplicationService.syncGuestCart(CART_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<CartCatalogLineKey>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(productCatalogPort).resolveLines(keysCaptor.capture());
        assertThat(keysCaptor.getValue()).hasSize(1);
        CartCatalogLineKey key = keysCaptor.getValue().iterator().next();
        assertThat(key.productId()).isEqualTo(PRODUCT_ID.getValue());
        assertThat(key.productVariantId()).isEqualTo(VARIANT_ID.getValue());
    }

    private CartCatalogLineView catalogView(boolean purchasable, int stockQuantity) {
        return new CartCatalogLineView(
                PRODUCT_ID.getValue(),
                VARIANT_ID.getValue(),
                "Phone",
                "Brand",
                "SKU",
                "https://img",
                new BigDecimal("100.00"),
                purchasable,
                stockQuantity,
                List.of(new CartCatalogOptionLineView(
                        1,
                        UUID.randomUUID(),
                        "Color",
                        UUID.randomUUID(),
                        "Black"
                ))
        );
    }
}
