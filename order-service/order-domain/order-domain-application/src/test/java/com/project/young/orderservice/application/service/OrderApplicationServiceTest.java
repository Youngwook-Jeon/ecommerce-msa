package com.project.young.orderservice.application.service;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.application.dto.command.PlaceOrderCommand;
import com.project.young.orderservice.application.port.output.CartCheckoutPort;
import com.project.young.orderservice.application.port.output.IdGenerator;
import com.project.young.orderservice.application.port.output.InventoryReservationClientException;
import com.project.young.orderservice.application.port.output.InventoryReservationConflictException;
import com.project.young.orderservice.application.port.output.InventoryReservationPort;
import com.project.young.orderservice.application.port.output.InventoryReservationUnavailableException;
import com.project.young.orderservice.application.port.output.view.ReserveInventoryLineResultView;
import com.project.young.orderservice.application.port.output.view.ReserveInventoryLineView;
import com.project.young.orderservice.application.port.output.view.ReserveInventoryResultView;
import com.project.young.orderservice.application.support.OrderPlacementTxExecutor;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.exception.OrderCheckoutValidationException;
import com.project.young.orderservice.domain.exception.OrderDomainException;
import com.project.young.orderservice.domain.exception.OrderNotFoundException;
import com.project.young.orderservice.domain.exception.OrderStateConflictException;
import com.project.young.orderservice.domain.repository.OrderRepository;
import com.project.young.orderservice.domain.sync.CartSyncChange;
import com.project.young.orderservice.domain.sync.CartSyncResult;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.CartItemId;
import com.project.young.orderservice.domain.valueobject.CartItemOptionLine;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.OrderStatus;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    private static final UserId USER_ID = new UserId("user-order-1");
    private static final CartId CART_ID = new CartId(UUID.randomUUID());
    private static final ProductId PRODUCT_ID = new ProductId(UUID.randomUUID());
    private static final ProductVariantId VARIANT_ID = new ProductVariantId(UUID.randomUUID());
    private static final UUID GENERATED_LINE_ID = UUID.randomUUID();
    private static final UUID GENERATED_ORDER_ID = UUID.randomUUID();
    private static final Instant FIXED_NOW = Instant.parse("2026-07-16T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-07-16T00:15:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartCheckoutPort cartCheckoutPort;

    @Mock
    private InventoryReservationPort inventoryReservationPort;

    @Mock
    private OrderPlacementTxExecutor orderPlacementTxExecutor;

    @Mock
    private IdGenerator idGenerator;

    private OrderApplicationService orderApplicationService;

    @BeforeEach
    void setUp() {
        orderApplicationService = new OrderApplicationService(
                orderRepository,
                cartCheckoutPort,
                inventoryReservationPort,
                orderPlacementTxExecutor,
                idGenerator,
                CLOCK
        );
        // Default: run the action immediately (simulates successful REQUIRES_NEW commit).
        lenient().doAnswer(invocation -> {
            Runnable action = invocation.getArgument(0);
            action.run();
            return null;
        }).when(orderPlacementTxExecutor).runInNewTransaction(any(Runnable.class));
        lenient().doAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(0);
            return action.get();
        }).when(orderPlacementTxExecutor).executeInNewTransaction(any());
    }

    @Test
    @DisplayName("placeOrder: 재고 예약 후 PENDING_PAYMENT 주문을 생성하고 카트는 유지한다")
    void placeOrder_reservesInventoryCreatesOrderAndKeepsCart() {
        Cart cart = cartWithOneItem();
        when(cartCheckoutPort.syncForCheckout(USER_ID))
                .thenReturn(new CartSyncResult(cart, List.of()));
        when(idGenerator.generateId()).thenReturn(GENERATED_ORDER_ID, GENERATED_LINE_ID);
        when(inventoryReservationPort.reserve(eq(GENERATED_ORDER_ID), any()))
                .thenReturn(validReserveResult(2));

        Order order = orderApplicationService.placeOrder(USER_ID, placeOrderCommand());

        assertThat(order.getId()).isEqualTo(new OrderId(GENERATED_ORDER_ID));
        assertThat(order.getUserId()).isEqualTo(USER_ID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.lineCount()).isEqualTo(1);
        assertThat(order.getLines().getFirst().getQuantity()).isEqualTo(2);
        assertThat(order.getShippingAddress().recipientName()).isEqualTo("Kim Young");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReserveInventoryLineView>> reserveLinesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(inventoryReservationPort).reserve(
                eq(GENERATED_ORDER_ID),
                reserveLinesCaptor.capture()
        );
        assertThat(reserveLinesCaptor.getValue()).containsExactly(
                new ReserveInventoryLineView(VARIANT_ID.getValue(), 2)
        );

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        InOrder inOrder = inOrder(inventoryReservationPort, orderPlacementTxExecutor, orderRepository);
        inOrder.verify(inventoryReservationPort).reserve(eq(GENERATED_ORDER_ID), any());
        inOrder.verify(orderPlacementTxExecutor).runInNewTransaction(any(Runnable.class));
        verify(orderRepository).insert(orderCaptor.capture());

        assertThat(orderCaptor.getValue().getId()).isEqualTo(new OrderId(GENERATED_ORDER_ID));
        verify(cartCheckoutPort, never()).clearAfterPayment(any());
    }

    @Test
    @DisplayName("placeOrder: 재고 예약 409면 OrderCheckoutValidationException")
    void placeOrder_inventoryConflict_throwsCheckoutValidation() {
        Cart cart = cartWithOneItem();
        when(cartCheckoutPort.syncForCheckout(USER_ID))
                .thenReturn(new CartSyncResult(cart, List.of()));
        when(idGenerator.generateId()).thenReturn(GENERATED_ORDER_ID);
        when(inventoryReservationPort.reserve(eq(GENERATED_ORDER_ID), any()))
                .thenThrow(new InventoryReservationConflictException("conflict"));

        assertThatThrownBy(() -> orderApplicationService.placeOrder(USER_ID, placeOrderCommand()))
                .isInstanceOf(OrderCheckoutValidationException.class)
                .hasMessageContaining("Insufficient inventory");

        verify(orderPlacementTxExecutor, never()).runInNewTransaction(any(Runnable.class));
        verify(orderRepository, never()).insert(any());
        verify(cartCheckoutPort, never()).clearAfterPayment(any());
        verify(inventoryReservationPort, never()).release(any());
    }

    @Test
    @DisplayName("placeOrder: 재고 서비스 불가면 InventoryReservationUnavailableException 전파")
    void placeOrder_inventoryUnavailable_propagates() {
        Cart cart = cartWithOneItem();
        when(cartCheckoutPort.syncForCheckout(USER_ID))
                .thenReturn(new CartSyncResult(cart, List.of()));
        when(idGenerator.generateId()).thenReturn(GENERATED_ORDER_ID);
        when(inventoryReservationPort.reserve(eq(GENERATED_ORDER_ID), any()))
                .thenThrow(new InventoryReservationUnavailableException("down", null));

        assertThatThrownBy(() -> orderApplicationService.placeOrder(USER_ID, placeOrderCommand()))
                .isInstanceOf(InventoryReservationUnavailableException.class);

        verify(orderPlacementTxExecutor, never()).runInNewTransaction(any(Runnable.class));
        verify(orderRepository, never()).insert(any());
        verify(cartCheckoutPort, never()).clearAfterPayment(any());
    }

    @Test
    @DisplayName("placeOrder: reserve 응답이 요청과 불일치하면 InventoryReservationClientException")
    void placeOrder_invalidReserveResponse_throwsClientException() {
        Cart cart = cartWithOneItem();
        when(cartCheckoutPort.syncForCheckout(USER_ID))
                .thenReturn(new CartSyncResult(cart, List.of()));
        when(idGenerator.generateId()).thenReturn(GENERATED_ORDER_ID);
        when(inventoryReservationPort.reserve(eq(GENERATED_ORDER_ID), any()))
                .thenReturn(new ReserveInventoryResultView(
                        GENERATED_ORDER_ID,
                        EXPIRES_AT,
                        false,
                        List.of(new ReserveInventoryLineResultView(
                                UUID.randomUUID(),
                                VARIANT_ID.getValue(),
                                99,
                                "ACTIVE"
                        ))
                ));

        assertThatThrownBy(() -> orderApplicationService.placeOrder(USER_ID, placeOrderCommand()))
                .isInstanceOf(InventoryReservationClientException.class)
                .hasMessageContaining("do not match");

        verify(orderPlacementTxExecutor, never()).runInNewTransaction(any(Runnable.class));
        verify(inventoryReservationPort).release(GENERATED_ORDER_ID);
    }

    @Test
    @DisplayName("placeOrder: expiresAt이 clock skew 허용 범위 안이면 통과한다")
    void placeOrder_expiresAtWithinClockSkew_succeeds() {
        Cart cart = cartWithOneItem();
        when(cartCheckoutPort.syncForCheckout(USER_ID))
                .thenReturn(new CartSyncResult(cart, List.of()));
        when(idGenerator.generateId()).thenReturn(GENERATED_ORDER_ID, GENERATED_LINE_ID);
        when(inventoryReservationPort.reserve(eq(GENERATED_ORDER_ID), any()))
                .thenReturn(reserveResult(FIXED_NOW.minusSeconds(29), 2));

        Order order = orderApplicationService.placeOrder(USER_ID, placeOrderCommand());

        assertThat(order.getId()).isEqualTo(new OrderId(GENERATED_ORDER_ID));
        verify(orderRepository).insert(any());
    }

    @Test
    @DisplayName("placeOrder: expiresAt이 clock skew를 초과해 과거면 InventoryReservationClientException")
    void placeOrder_expiresAtBeyondClockSkew_throwsClientException() {
        Cart cart = cartWithOneItem();
        when(cartCheckoutPort.syncForCheckout(USER_ID))
                .thenReturn(new CartSyncResult(cart, List.of()));
        when(idGenerator.generateId()).thenReturn(GENERATED_ORDER_ID);
        when(inventoryReservationPort.reserve(eq(GENERATED_ORDER_ID), any()))
                .thenReturn(reserveResult(FIXED_NOW.minusSeconds(30), 2));

        assertThatThrownBy(() -> orderApplicationService.placeOrder(USER_ID, placeOrderCommand()))
                .isInstanceOf(InventoryReservationClientException.class)
                .hasMessageContaining("expiresAt");

        verify(inventoryReservationPort).release(GENERATED_ORDER_ID);
        verify(orderPlacementTxExecutor, never()).runInNewTransaction(any(Runnable.class));
    }

    @Test
    @DisplayName("placeOrder: reserve 응답 lines가 null이면 InventoryReservationClientException")
    void placeOrder_nullReserveLines_throwsClientException() {
        Cart cart = cartWithOneItem();
        when(cartCheckoutPort.syncForCheckout(USER_ID))
                .thenReturn(new CartSyncResult(cart, List.of()));
        when(idGenerator.generateId()).thenReturn(GENERATED_ORDER_ID);

        ReserveInventoryResultView result = org.mockito.Mockito.mock(ReserveInventoryResultView.class);
        when(result.checkoutId()).thenReturn(GENERATED_ORDER_ID);
        when(result.expiresAt()).thenReturn(EXPIRES_AT);
        when(result.lines()).thenReturn(null);
        when(inventoryReservationPort.reserve(eq(GENERATED_ORDER_ID), any())).thenReturn(result);

        assertThatThrownBy(() -> orderApplicationService.placeOrder(USER_ID, placeOrderCommand()))
                .isInstanceOf(InventoryReservationClientException.class)
                .hasMessageContaining("lines must not be null");

        verify(inventoryReservationPort).release(GENERATED_ORDER_ID);
        verify(orderPlacementTxExecutor, never()).runInNewTransaction(any(Runnable.class));
    }

    @Test
    @DisplayName("placeOrder: 주문 persist(커밋 포함) 실패 시 release 보상 후 예외 전파")
    void placeOrder_persistFailure_releasesReservationAndRethrows() {
        Cart cart = cartWithOneItem();
        when(cartCheckoutPort.syncForCheckout(USER_ID))
                .thenReturn(new CartSyncResult(cart, List.of()));
        when(idGenerator.generateId()).thenReturn(GENERATED_ORDER_ID, GENERATED_LINE_ID);
        when(inventoryReservationPort.reserve(eq(GENERATED_ORDER_ID), any()))
                .thenReturn(validReserveResult(2));

        RuntimeException persistFailure = new RuntimeException("commit failed");
        doThrow(persistFailure).when(orderPlacementTxExecutor).runInNewTransaction(any(Runnable.class));

        assertThatThrownBy(() -> orderApplicationService.placeOrder(USER_ID, placeOrderCommand()))
                .isSameAs(persistFailure);

        InOrder inOrder = inOrder(inventoryReservationPort, orderPlacementTxExecutor);
        inOrder.verify(inventoryReservationPort).reserve(eq(GENERATED_ORDER_ID), any());
        inOrder.verify(orderPlacementTxExecutor).runInNewTransaction(any(Runnable.class));
        inOrder.verify(inventoryReservationPort).release(GENERATED_ORDER_ID);
        verify(cartCheckoutPort, never()).clearAfterPayment(any());
    }

    @Test
    @DisplayName("placeOrder: 카트 동기화 변경이 있으면 OrderCheckoutValidationException")
    void placeOrder_cartChangedDuringCheckout_throws() {
        Cart cart = cartWithOneItem();
        CartSyncChange change = new CartSyncChange.PriceUpdated(
                cart.getItems().getFirst().getId(),
                new Money(new BigDecimal("999.00")),
                new Money(new BigDecimal("1099.00"))
        );
        when(cartCheckoutPort.syncForCheckout(USER_ID))
                .thenReturn(new CartSyncResult(cart, List.of(change)));

        assertThatThrownBy(() -> orderApplicationService.placeOrder(USER_ID, placeOrderCommand()))
                .isInstanceOf(OrderCheckoutValidationException.class)
                .hasMessageContaining("Cart changed during checkout");

        verify(inventoryReservationPort, never()).reserve(any(), any());
        verify(orderPlacementTxExecutor, never()).runInNewTransaction(any(Runnable.class));
        verify(cartCheckoutPort, never()).clearAfterPayment(any());
    }

    @Test
    @DisplayName("placeOrder: 동기화 후 빈 카트면 OrderDomainException")
    void placeOrder_emptyCartAfterSync_throws() {
        Cart emptyCart = Cart.createForUser(USER_ID, CART_ID);
        when(cartCheckoutPort.syncForCheckout(USER_ID))
                .thenReturn(new CartSyncResult(emptyCart, List.of()));

        assertThatThrownBy(() -> orderApplicationService.placeOrder(USER_ID, placeOrderCommand()))
                .isInstanceOf(OrderDomainException.class)
                .hasMessageContaining("empty cart");

        verify(inventoryReservationPort, never()).reserve(any(), any());
        verify(orderPlacementTxExecutor, never()).runInNewTransaction(any(Runnable.class));
    }

    @Test
    @DisplayName("placeOrder: null command면 예외")
    void placeOrder_nullCommand_throws() {
        assertThatThrownBy(() -> orderApplicationService.placeOrder(USER_ID, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("confirmPayment: inventory confirm 후 주문을 CONFIRMED로 바꾸고 카트를 비운다")
    void confirmPayment_confirmsInventoryTransitionsOrderAndClearsCart() {
        Order order = storedOrder(OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findByIdAndUserId(new OrderId(GENERATED_ORDER_ID), USER_ID))
                .thenReturn(Optional.of(order));
        when(orderRepository.updateStatus(order, OrderStatus.PENDING_PAYMENT)).thenReturn(true);

        Order result = orderApplicationService.confirmPayment(
                USER_ID,
                new OrderId(GENERATED_ORDER_ID)
        );

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        InOrder inOrder = inOrder(inventoryReservationPort, orderPlacementTxExecutor, cartCheckoutPort);
        inOrder.verify(inventoryReservationPort).confirm(GENERATED_ORDER_ID);
        inOrder.verify(orderPlacementTxExecutor).executeInNewTransaction(any());
        inOrder.verify(cartCheckoutPort).clearAfterPayment(order);
        verify(orderRepository).updateStatus(order, OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("confirmPayment: 이미 CONFIRMED면 inventory를 재확정하지 않고 카트 정리만 재시도한다")
    void confirmPayment_alreadyConfirmed_isIdempotent() {
        Order order = storedOrder(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndUserId(new OrderId(GENERATED_ORDER_ID), USER_ID))
                .thenReturn(Optional.of(order));

        Order result = orderApplicationService.confirmPayment(
                USER_ID,
                new OrderId(GENERATED_ORDER_ID)
        );

        assertThat(result).isSameAs(order);
        verify(inventoryReservationPort, never()).confirm(any());
        verify(orderPlacementTxExecutor, never()).executeInNewTransaction(any());
        verify(cartCheckoutPort).clearAfterPayment(order);
    }

    @Test
    @DisplayName("cancelOrder: 주문을 CANCELLED로 커밋한 후 inventory를 release한다")
    void cancelOrder_transitionsOrderThenReleasesInventory() {
        Order order = storedOrder(OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findByIdAndUserId(new OrderId(GENERATED_ORDER_ID), USER_ID))
                .thenReturn(Optional.of(order));
        when(orderRepository.updateStatus(order, OrderStatus.PENDING_PAYMENT)).thenReturn(true);

        Order result = orderApplicationService.cancelOrder(
                USER_ID,
                new OrderId(GENERATED_ORDER_ID)
        );

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        InOrder inOrder = inOrder(orderPlacementTxExecutor, inventoryReservationPort);
        inOrder.verify(orderPlacementTxExecutor).executeInNewTransaction(any());
        inOrder.verify(inventoryReservationPort).release(GENERATED_ORDER_ID);
        verify(orderRepository).updateStatus(order, OrderStatus.PENDING_PAYMENT);
        verify(cartCheckoutPort, never()).clearAfterPayment(any());
    }

    @Test
    @DisplayName("cancelOrder: 이미 CANCELLED여도 release를 재시도한다")
    void cancelOrder_alreadyCancelled_retriesRelease() {
        Order order = storedOrder(OrderStatus.CANCELLED);
        when(orderRepository.findByIdAndUserId(new OrderId(GENERATED_ORDER_ID), USER_ID))
                .thenReturn(Optional.of(order));

        Order result = orderApplicationService.cancelOrder(
                USER_ID,
                new OrderId(GENERATED_ORDER_ID)
        );

        assertThat(result).isSameAs(order);
        verify(orderRepository, never()).updateStatus(any(), any());
        verify(inventoryReservationPort).release(GENERATED_ORDER_ID);
    }

    @Test
    @DisplayName("confirmPayment: CANCELLED 주문은 OrderStateConflictException")
    void confirmPayment_cancelledOrder_throwsStateConflict() {
        Order order = storedOrder(OrderStatus.CANCELLED);
        when(orderRepository.findByIdAndUserId(new OrderId(GENERATED_ORDER_ID), USER_ID))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderApplicationService.confirmPayment(
                USER_ID,
                new OrderId(GENERATED_ORDER_ID)
        ))
                .isInstanceOf(OrderStateConflictException.class)
                .hasMessageContaining("CANCELLED");

        verify(inventoryReservationPort, never()).confirm(any());
        verify(cartCheckoutPort, never()).clearAfterPayment(any());
    }

    @Test
    @DisplayName("confirmPayment: CAS 실패면 OrderStateConflictException")
    void confirmPayment_casFailure_throwsStateConflict() {
        Order order = storedOrder(OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findByIdAndUserId(new OrderId(GENERATED_ORDER_ID), USER_ID))
                .thenReturn(Optional.of(order));
        when(orderRepository.updateStatus(order, OrderStatus.PENDING_PAYMENT)).thenReturn(false);

        assertThatThrownBy(() -> orderApplicationService.confirmPayment(
                USER_ID,
                new OrderId(GENERATED_ORDER_ID)
        ))
                .isInstanceOf(OrderStateConflictException.class)
                .hasMessageContaining("concurrently");

        verify(inventoryReservationPort).confirm(GENERATED_ORDER_ID);
        verify(cartCheckoutPort, never()).clearAfterPayment(any());
    }

    @Test
    @DisplayName("getOrder: 사용자 주문을 조회한다")
    void getOrder_returnsOrder() {
        Order order = Order.builder()
                .orderId(new OrderId(GENERATED_ORDER_ID))
                .userId(USER_ID)
                .status(OrderStatus.CONFIRMED)
                .shippingAddress(new com.project.young.orderservice.domain.valueobject.ShippingAddress(
                        "Kim Young", "01012345678", "123 Main St", null, "Seoul", "04524", "KR"))
                .lines(List.of())
                .subtotalAmount(Money.ZERO)
                .shippingAmount(Money.ZERO)
                .totalAmount(Money.ZERO)
                .build();

        when(orderRepository.findByIdAndUserId(new OrderId(GENERATED_ORDER_ID), USER_ID))
                .thenReturn(Optional.of(order));

        Order result = orderApplicationService.getOrder(USER_ID, new OrderId(GENERATED_ORDER_ID));

        assertThat(result).isEqualTo(order);
    }

    @Test
    @DisplayName("getOrder: 주문이 없으면 OrderNotFoundException")
    void getOrder_notFound_throws() {
        when(orderRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderApplicationService.getOrder(
                USER_ID,
                new OrderId(GENERATED_ORDER_ID)
        ))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Order not found");
    }

    private static Order storedOrder(OrderStatus status) {
        return Order.builder()
                .orderId(new OrderId(GENERATED_ORDER_ID))
                .userId(USER_ID)
                .status(status)
                .shippingAddress(new com.project.young.orderservice.domain.valueobject.ShippingAddress(
                        "Kim Young", "01012345678", "123 Main St", null, "Seoul", "04524", "KR"))
                .lines(List.of())
                .subtotalAmount(Money.ZERO)
                .shippingAmount(Money.ZERO)
                .totalAmount(Money.ZERO)
                .build();
    }

    private static ReserveInventoryResultView validReserveResult(int quantity) {
        return reserveResult(EXPIRES_AT, quantity);
    }

    private static ReserveInventoryResultView reserveResult(Instant expiresAt, int quantity) {
        return new ReserveInventoryResultView(
                GENERATED_ORDER_ID,
                expiresAt,
                false,
                List.of(new ReserveInventoryLineResultView(
                        UUID.randomUUID(),
                        VARIANT_ID.getValue(),
                        quantity,
                        "ACTIVE"
                ))
        );
    }

    private static Cart cartWithOneItem() {
        Cart cart = Cart.createForUser(USER_ID, CART_ID);
        cart.addOrMergeItem(
                PRODUCT_ID,
                VARIANT_ID,
                sampleSnapshot("999.00"),
                2,
                new CartItemId(UUID.randomUUID())
        );
        return cart;
    }

    private static PlaceOrderCommand placeOrderCommand() {
        return PlaceOrderCommand.builder()
                .recipientName("Kim Young")
                .phone("01012345678")
                .addressLine1("123 Main St")
                .addressLine2("Apt 4B")
                .city("Seoul")
                .postalCode("04524")
                .countryCode("KR")
                .build();
    }

    private static CartItemSnapshot sampleSnapshot(String price) {
        return new CartItemSnapshot(
                "iPhone 15 Pro",
                "Apple",
                "SKU-001",
                "https://cdn.example.com/image.jpg",
                new Money(new BigDecimal(price)),
                List.of(new CartItemOptionLine(
                        1,
                        UUID.randomUUID(),
                        "Color",
                        UUID.randomUUID(),
                        "Black"
                ))
        );
    }
}
