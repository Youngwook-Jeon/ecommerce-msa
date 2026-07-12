package com.project.young.orderservice.application.service;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.application.mapper.CartCatalogMapper;
import com.project.young.orderservice.application.port.output.CartCatalogLineKey;
import com.project.young.orderservice.application.port.output.IdGenerator;
import com.project.young.orderservice.application.port.output.ProductCatalogPort;
import com.project.young.orderservice.application.port.output.ProductCatalogUnavailableException;
import com.project.young.orderservice.application.port.output.view.CartCatalogLineView;
import com.project.young.orderservice.application.port.output.view.CartCatalogOptionLineView;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.exception.CartDomainException;
import com.project.young.orderservice.domain.exception.CartNotFoundException;
import com.project.young.orderservice.domain.merge.CartMergeResult;
import com.project.young.orderservice.domain.merge.CartMergeSkipReason;
import com.project.young.orderservice.domain.repository.CartRepository;
import org.springframework.dao.OptimisticLockingFailureException;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartApplicationServiceTest {

    private static final UserId USER_ID = new UserId("user-1");
    private static final CartId CART_ID = new CartId(UUID.randomUUID());
    private static final CartId GUEST_CART_ID = new CartId(UUID.randomUUID());
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
    @DisplayName("addItem: 신규 사용자는 카트를 만든 뒤 라인을 추가한다")
    void addItem_createsUserCartWhenMissing() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(idGenerator.generateId()).thenReturn(CART_ID.getValue(), UUID.randomUUID());
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of(
                VARIANT_ID.getValue(),
                catalogView(true, 5)
        ));

        Cart updated = cartApplicationService.addItem(
                CartOwner.forUser(USER_ID), PRODUCT_ID, VARIANT_ID, 1);

        assertThat(updated.itemCount()).isEqualTo(1);
        assertThat(updated.isUserCart()).isTrue();
        assertThat(updated.getUserId()).isEqualTo(USER_ID);
        verify(cartRepository).insert(any(Cart.class));
        verify(cartRepository).update(any(Cart.class));
    }

    @Test
    @DisplayName("addItem: 기존 카트에 라인을 추가하고 update 한다")
    void addItem_addsLineAndUpdatesCart() {
        Cart cart = Cart.createForUser(USER_ID, CART_ID);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(idGenerator.generateId()).thenReturn(UUID.randomUUID());
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of(
                VARIANT_ID.getValue(),
                catalogView(true, 5)
        ));

        Cart updated = cartApplicationService.addItem(
                CartOwner.forUser(USER_ID), PRODUCT_ID, VARIANT_ID, 2);

        assertThat(updated.itemCount()).isEqualTo(1);
        assertThat(updated.getItems().getFirst().getQuantity()).isEqualTo(2);
        assertThat(updated.getItems().getFirst().getSnapshot().productName()).isEqualTo("Phone");
        verify(cartRepository).update(cart);
        verify(cartRepository, never()).findById(any());
    }

    @Test
    @DisplayName("addItem: variant를 찾지 못하면 예외")
    void addItem_variantNotFound() {
        Cart cart = Cart.createForUser(USER_ID, CART_ID);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of());

        assertThatThrownBy(() -> cartApplicationService.addItem(
                CartOwner.forUser(USER_ID), PRODUCT_ID, VARIANT_ID, 1))
                .isInstanceOf(CartDomainException.class)
                .hasMessageContaining("not found");
        verify(cartRepository, never()).update(any());
    }

    @Test
    @DisplayName("addItem: 재고 부족이면 예외")
    void addItem_insufficientStock() {
        Cart cart = Cart.createForUser(USER_ID, CART_ID);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of(
                VARIANT_ID.getValue(),
                catalogView(true, 1)
        ));

        assertThatThrownBy(() -> cartApplicationService.addItem(
                CartOwner.forUser(USER_ID), PRODUCT_ID, VARIANT_ID, 2))
                .isInstanceOf(CartDomainException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    @DisplayName("updateItemQuantity: 카트가 없으면 CartNotFoundException")
    void updateItemQuantity_cartNotFound() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartApplicationService.updateItemQuantity(
                CartOwner.forUser(USER_ID),
                new CartItemId(UUID.randomUUID()),
                2
        )).isInstanceOf(CartNotFoundException.class);
    }

    @Test
    @DisplayName("syncCart: cart item 키로 catalog를 조회하고 reconcile 결과를 저장한다")
    void syncCart_reconcilesAndUpdates() {
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

        CartSyncResult result = cartApplicationService.syncCart(CartOwner.forGuest(CART_ID));

        assertThat(result.changes()).isNotEmpty();
        assertThat(result.changes().getFirst()).isInstanceOf(CartSyncChange.PriceUpdated.class);
        verify(productCatalogPort).resolveLines(any());
        verify(guestCartRepository).update(cart);
    }

    @Test
    @DisplayName("syncCart: 변경점이 없으면 repository update를 건너뛴다")
    void syncCart_noChanges_skipsUpdate() {
        CartCatalogLineView catalog = catalogView(true, 5);
        Cart cart = Cart.createForGuest(CART_ID);
        cart.addOrMergeItem(
                PRODUCT_ID,
                VARIANT_ID,
                CartCatalogMapper.toSnapshot(catalog),
                1,
                new CartItemId(UUID.randomUUID())
        );
        when(guestCartRepository.findById(CART_ID)).thenReturn(Optional.of(cart));
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of(
                VARIANT_ID.getValue(),
                catalog
        ));

        CartSyncResult result = cartApplicationService.syncCart(CartOwner.forGuest(CART_ID));

        assertThat(result.changes()).isEmpty();
        verify(guestCartRepository, never()).update(any());
    }

    @Test
    @DisplayName("syncCart: 빈 카트는 catalog 호출 없이 빈 변경 목록을 반환한다")
    void syncCart_emptyCart() {
        Cart cart = Cart.createForGuest(CART_ID);
        when(guestCartRepository.findById(CART_ID)).thenReturn(Optional.of(cart));

        CartSyncResult result = cartApplicationService.syncCart(CartOwner.forGuest(CART_ID));

        assertThat(result.changes()).isEmpty();
        verify(productCatalogPort, never()).resolveLines(any());
        verify(guestCartRepository, never()).update(any());
    }

    @Test
    @DisplayName("syncCart: cart item별 productId를 catalog key에 포함한다")
    void syncCart_passesProductIdInKeys() {
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

        cartApplicationService.syncCart(CartOwner.forGuest(CART_ID));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<CartCatalogLineKey>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(productCatalogPort).resolveLines(keysCaptor.capture());
        assertThat(keysCaptor.getValue()).hasSize(1);
        CartCatalogLineKey key = keysCaptor.getValue().iterator().next();
        assertThat(key.productId()).isEqualTo(PRODUCT_ID.getValue());
        assertThat(key.productVariantId()).isEqualTo(VARIANT_ID.getValue());
    }

    @Test
    @DisplayName("mergeGuestCartIntoUser: 게스트 라인을 사용자 카트로 병합하고 게스트 카트를 삭제한다")
    void mergeGuestCartIntoUser_mergesGuestLinesIntoUserCart() {
        Cart guestCart = guestCartWithItem(VARIANT_ID, 2);
        when(guestCartRepository.findById(GUEST_CART_ID)).thenReturn(Optional.of(guestCart));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(idGenerator.generateId()).thenReturn(CART_ID.getValue(), UUID.randomUUID());
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of(
                VARIANT_ID.getValue(),
                catalogView(true, 5)
        ));

        CartMergeResult result = cartApplicationService.mergeGuestCartIntoUser(USER_ID, GUEST_CART_ID);

        assertThat(result.mergedLineCount()).isEqualTo(1);
        assertThat(result.skippedLines()).isEmpty();
        assertThat(result.cart()).isNotNull();
        assertThat(result.cart().itemCount()).isEqualTo(1);
        assertThat(result.cart().getItems().getFirst().getQuantity()).isEqualTo(2);
        verify(cartRepository).insert(any(Cart.class));
        verify(cartRepository).update(any(Cart.class));
        verify(guestCartRepository).delete(GUEST_CART_ID);
    }

    @Test
    @DisplayName("mergeGuestCartIntoUser: 동일 variant는 수량을 합산한다")
    void mergeGuestCartIntoUser_mergesSameVariantQuantities() {
        Cart guestCart = guestCartWithItem(VARIANT_ID, 2);
        Cart userCart = Cart.createForUser(USER_ID, CART_ID);
        CartItemId existingItemId = new CartItemId(UUID.randomUUID());
        userCart.addOrMergeItem(
                PRODUCT_ID,
                VARIANT_ID,
                sampleSnapshot("90.00"),
                1,
                existingItemId
        );

        when(guestCartRepository.findById(GUEST_CART_ID)).thenReturn(Optional.of(guestCart));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(userCart));
        when(idGenerator.generateId()).thenReturn(UUID.randomUUID());
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of(
                VARIANT_ID.getValue(),
                catalogView(true, 10)
        ));

        CartMergeResult result = cartApplicationService.mergeGuestCartIntoUser(USER_ID, GUEST_CART_ID);

        assertThat(result.mergedLineCount()).isEqualTo(1);
        assertThat(result.cart().itemCount()).isEqualTo(1);
        assertThat(result.cart().getItems().getFirst().getQuantity()).isEqualTo(3);
        verify(cartRepository, never()).insert(any());
        verify(guestCartRepository).delete(GUEST_CART_ID);
    }

    @Test
    @DisplayName("mergeGuestCartIntoUser: MAX_LINE_ITEMS 초과 라인은 스킵한다")
    void mergeGuestCartIntoUser_skipsWhenMaxLineItemsExceeded() {
        Cart userCart = Cart.createForUser(USER_ID, CART_ID);
        for (int i = 0; i < Cart.MAX_LINE_ITEMS; i++) {
            userCart.addOrMergeItem(
                    PRODUCT_ID,
                    new ProductVariantId(UUID.randomUUID()),
                    sampleSnapshot("90.00"),
                    1,
                    new CartItemId(UUID.randomUUID())
            );
        }

        ProductVariantId guestOnlyVariant = new ProductVariantId(UUID.randomUUID());
        Cart guestCart = guestCartWithItem(guestOnlyVariant, 1);

        when(guestCartRepository.findById(GUEST_CART_ID)).thenReturn(Optional.of(guestCart));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(userCart));
        when(productCatalogPort.resolveLines(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Collection<CartCatalogLineKey> keys = invocation.getArgument(0);
            Map<UUID, CartCatalogLineView> resolved = new HashMap<>();
            for (CartCatalogLineKey key : keys) {
                resolved.put(
                        key.productVariantId(),
                        catalogViewForVariant(new ProductVariantId(key.productVariantId()), true, 5)
                );
            }
            return resolved;
        });

        CartMergeResult result = cartApplicationService.mergeGuestCartIntoUser(USER_ID, GUEST_CART_ID);

        assertThat(result.mergedLineCount()).isEqualTo(0);
        assertThat(result.skippedLines()).hasSize(1);
        assertThat(result.skippedLines().getFirst().reason()).isEqualTo(CartMergeSkipReason.MAX_LINE_ITEMS_EXCEEDED);
        assertThat(result.cart().itemCount()).isEqualTo(Cart.MAX_LINE_ITEMS);
        verify(guestCartRepository).delete(GUEST_CART_ID);
    }

    @Test
    @DisplayName("mergeGuestCartIntoUser: 게스트 카트가 없으면 no-op")
    void mergeGuestCartIntoUser_noGuestCart() {
        Cart userCart = Cart.createForUser(USER_ID, CART_ID);
        when(guestCartRepository.findById(GUEST_CART_ID)).thenReturn(Optional.empty());
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(userCart));

        CartMergeResult result = cartApplicationService.mergeGuestCartIntoUser(USER_ID, GUEST_CART_ID);

        assertThat(result.mergedLineCount()).isEqualTo(0);
        assertThat(result.cart()).isSameAs(userCart);
        verify(productCatalogPort, never()).resolveLines(any());
        verify(cartRepository, never()).update(any());
        verify(guestCartRepository, never()).delete(any());
    }

    @Test
    @DisplayName("mergeGuestCartIntoUser: 빈 게스트 카트는 삭제만 하고 병합하지 않는다")
    void mergeGuestCartIntoUser_emptyGuestCart() {
        Cart guestCart = Cart.createForGuest(GUEST_CART_ID);
        when(guestCartRepository.findById(GUEST_CART_ID)).thenReturn(Optional.of(guestCart));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        CartMergeResult result = cartApplicationService.mergeGuestCartIntoUser(USER_ID, GUEST_CART_ID);

        assertThat(result.mergedLineCount()).isEqualTo(0);
        assertThat(result.cart()).isNull();
        verify(guestCartRepository).delete(GUEST_CART_ID);
        verify(productCatalogPort, never()).resolveLines(any());
        verify(cartRepository, never()).update(any());
    }

    @Test
    @DisplayName("mergeGuestCartIntoUser: catalog 장애 시 병합을 중단하고 저장하지 않는다")
    void mergeGuestCartIntoUser_catalogUnavailable_abortsWithoutMutation() {
        Cart guestCart = guestCartWithItem(VARIANT_ID, 1);
        when(guestCartRepository.findById(GUEST_CART_ID)).thenReturn(Optional.of(guestCart));
        when(productCatalogPort.resolveLines(any()))
                .thenThrow(new ProductCatalogUnavailableException("down", new RuntimeException("503")));

        assertThatThrownBy(() -> cartApplicationService.mergeGuestCartIntoUser(USER_ID, GUEST_CART_ID))
                .isInstanceOf(ProductCatalogUnavailableException.class);

        verify(cartRepository, never()).update(any());
        verify(guestCartRepository, never()).delete(any());
    }

    @Test
    @DisplayName("mergeGuestCartIntoUser: 병합 후 sync로 재고를 조정한다")
    void mergeGuestCartIntoUser_syncAdjustsQuantityAfterMerge() {
        Cart guestCart = guestCartWithItem(VARIANT_ID, 5);
        when(guestCartRepository.findById(GUEST_CART_ID)).thenReturn(Optional.of(guestCart));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(idGenerator.generateId()).thenReturn(CART_ID.getValue(), UUID.randomUUID());
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of(
                VARIANT_ID.getValue(),
                catalogView(true, 2)
        ));

        CartMergeResult result = cartApplicationService.mergeGuestCartIntoUser(USER_ID, GUEST_CART_ID);

        assertThat(result.mergedLineCount()).isEqualTo(1);
        assertThat(result.cart().getItems().getFirst().getQuantity()).isEqualTo(2);
        assertThat(result.syncChanges()).isNotEmpty();
        assertThat(result.syncChanges().getFirst()).isInstanceOf(CartSyncChange.QuantityAdjusted.class);
    }

    @Test
    @DisplayName("mergeGuestCartIntoUser: catalog에서 variant를 찾지 못하면 스킵한다")
    void mergeGuestCartIntoUser_skipsVariantNotFound() {
        Cart guestCart = guestCartWithItem(VARIANT_ID, 1);
        when(guestCartRepository.findById(GUEST_CART_ID)).thenReturn(Optional.of(guestCart));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(idGenerator.generateId()).thenReturn(CART_ID.getValue());
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of());

        CartMergeResult result = cartApplicationService.mergeGuestCartIntoUser(USER_ID, GUEST_CART_ID);

        assertThat(result.mergedLineCount()).isEqualTo(0);
        assertThat(result.skippedLines()).hasSize(1);
        assertThat(result.skippedLines().getFirst().reason()).isEqualTo(CartMergeSkipReason.VARIANT_NOT_FOUND);
        verify(guestCartRepository).delete(GUEST_CART_ID);
    }

    @Test
    @DisplayName("mergeGuestCartIntoUser: 사용자 카트 저장 후 게스트 카트를 삭제한다")
    void mergeGuestCartIntoUser_persistsUserCartBeforeDeletingGuest() {
        Cart guestCart = guestCartWithItem(VARIANT_ID, 1);
        when(guestCartRepository.findById(GUEST_CART_ID)).thenReturn(Optional.of(guestCart));
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(idGenerator.generateId()).thenReturn(CART_ID.getValue(), UUID.randomUUID());
        when(productCatalogPort.resolveLines(any())).thenReturn(Map.of(
                VARIANT_ID.getValue(),
                catalogView(true, 5)
        ));

        cartApplicationService.mergeGuestCartIntoUser(USER_ID, GUEST_CART_ID);

        InOrder inOrder = inOrder(cartRepository, guestCartRepository);
        inOrder.verify(cartRepository).update(any(Cart.class));
        inOrder.verify(guestCartRepository).delete(GUEST_CART_ID);
    }

    @Test
    @DisplayName("mergeGuestCart: 낙관적 락 충돌 시 재시도하여 성공한다")
    void mergeGuestCart_retriesOnOptimisticLockConflict() {
        CartApplicationService self = mock(CartApplicationService.class);
        cartApplicationService.setSelf(self);
        CartMergeResult expected = new CartMergeResult(Cart.createForUser(USER_ID, CART_ID), 1, List.of(), List.of());
        when(self.mergeGuestCartIntoUser(USER_ID, GUEST_CART_ID))
                .thenThrow(new OptimisticLockingFailureException("conflict"))
                .thenReturn(expected);

        CartMergeResult result = cartApplicationService.mergeGuestCart(USER_ID, GUEST_CART_ID);

        assertThat(result).isSameAs(expected);
        verify(self, times(2)).mergeGuestCartIntoUser(USER_ID, GUEST_CART_ID);
    }

    @Test
    @DisplayName("mergeGuestCart: 재시도 횟수를 초과하면 예외를 던진다")
    void mergeGuestCart_rethrowsWhenRetriesExhausted() {
        CartApplicationService self = mock(CartApplicationService.class);
        cartApplicationService.setSelf(self);
        when(self.mergeGuestCartIntoUser(USER_ID, GUEST_CART_ID))
                .thenThrow(new OptimisticLockingFailureException("conflict"));

        assertThatThrownBy(() -> cartApplicationService.mergeGuestCart(USER_ID, GUEST_CART_ID))
                .isInstanceOf(OptimisticLockingFailureException.class);

        verify(self, times(2)).mergeGuestCartIntoUser(USER_ID, GUEST_CART_ID);
    }

    private Cart guestCartWithItem(ProductVariantId variantId, int quantity) {
        Cart guestCart = Cart.createForGuest(GUEST_CART_ID);
        guestCart.addOrMergeItem(
                PRODUCT_ID,
                variantId,
                sampleSnapshot("90.00"),
                quantity,
                new CartItemId(UUID.randomUUID())
        );
        return guestCart;
    }

    private com.project.young.orderservice.domain.valueobject.CartItemSnapshot sampleSnapshot(String price) {
        return new com.project.young.orderservice.domain.valueobject.CartItemSnapshot(
                "Old",
                "Brand",
                "SKU",
                null,
                new Money(new BigDecimal(price)),
                List.of()
        );
    }

    private CartCatalogLineView catalogViewForVariant(
            ProductVariantId variantId,
            boolean purchasable,
            int stockQuantity
    ) {
        return new CartCatalogLineView(
                PRODUCT_ID.getValue(),
                variantId.getValue(),
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

    private CartCatalogLineView catalogView(boolean purchasable, int stockQuantity) {
        return catalogViewForVariant(VARIANT_ID, purchasable, stockQuantity);
    }
}
