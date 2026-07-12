package com.project.young.orderservice.application.service;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.application.dto.command.PlaceOrderCommand;
import com.project.young.orderservice.application.port.output.CartCheckoutPort;
import com.project.young.orderservice.application.port.output.IdGenerator;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.exception.OrderCheckoutValidationException;
import com.project.young.orderservice.domain.exception.OrderDomainException;
import com.project.young.orderservice.domain.exception.OrderNotFoundException;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartCheckoutPort cartCheckoutPort;

    @Mock
    private IdGenerator idGenerator;

    @InjectMocks
    private OrderApplicationService orderApplicationService;

    @Test
    @DisplayName("placeOrder: 동기화된 카트로 주문을 생성하고 카트를 비운다")
    void placeOrder_createsOrderAndClearsCart() {
        Cart cart = cartWithOneItem();
        when(cartCheckoutPort.syncForCheckout(USER_ID))
                .thenReturn(new CartSyncResult(cart, List.of()));
        when(idGenerator.generateId()).thenReturn(GENERATED_LINE_ID, GENERATED_ORDER_ID);

        Order order = orderApplicationService.placeOrder(USER_ID, placeOrderCommand());

        assertThat(order.getId()).isEqualTo(new OrderId(GENERATED_ORDER_ID));
        assertThat(order.getUserId()).isEqualTo(USER_ID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.lineCount()).isEqualTo(1);
        assertThat(order.getLines().getFirst().getQuantity()).isEqualTo(2);
        assertThat(order.getShippingAddress().recipientName()).isEqualTo("Kim Young");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
        InOrder inOrder = inOrder(orderRepository, cartCheckoutPort);
        inOrder.verify(orderRepository).insert(orderCaptor.capture());
        inOrder.verify(cartCheckoutPort).clearAfterOrder(cartCaptor.capture());

        assertThat(orderCaptor.getValue().getId()).isEqualTo(new OrderId(GENERATED_ORDER_ID));
        assertThat(cartCaptor.getValue()).isSameAs(cart);
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

        verify(orderRepository, never()).insert(any());
        verify(cartCheckoutPort, never()).clearAfterOrder(any());
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

        verify(orderRepository, never()).insert(any());
    }

    @Test
    @DisplayName("placeOrder: null command면 예외")
    void placeOrder_nullCommand_throws() {
        assertThatThrownBy(() -> orderApplicationService.placeOrder(USER_ID, null))
                .isInstanceOf(NullPointerException.class);
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
