package com.project.young.orderservice.application.service;

import com.project.young.orderservice.application.dto.command.PlaceOrderCommand;
import com.project.young.orderservice.application.port.output.CartCheckoutPort;
import com.project.young.orderservice.application.port.output.IdGenerator;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.entity.OrderLine;
import com.project.young.orderservice.domain.exception.OrderCheckoutValidationException;
import com.project.young.orderservice.domain.exception.OrderDomainException;
import com.project.young.orderservice.domain.exception.OrderNotFoundException;
import com.project.young.orderservice.domain.repository.OrderRepository;
import com.project.young.orderservice.domain.sync.CartSyncResult;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.OrderLineId;
import com.project.young.orderservice.domain.valueobject.ShippingAddress;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class OrderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);

    private final OrderRepository orderRepository;
    private final CartCheckoutPort cartCheckoutPort;
    private final IdGenerator idGenerator;

    public OrderApplicationService(
            OrderRepository orderRepository,
            CartCheckoutPort cartCheckoutPort,
            IdGenerator idGenerator
    ) {
        this.orderRepository = orderRepository;
        this.cartCheckoutPort = cartCheckoutPort;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public Order placeOrder(UserId userId, PlaceOrderCommand command) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(command, "command must not be null");

        log.debug("Placing order for user {}", userId.value());

        ShippingAddress shippingAddress = toShippingAddress(command);

        CartSyncResult syncResult = cartCheckoutPort.syncForCheckout(userId);
        if (!syncResult.changes().isEmpty()) {
            log.warn(
                    "Checkout rejected for user {}: cart changed during sync ({} change(s))",
                    userId.value(),
                    syncResult.changes().size()
            );
            throw new OrderCheckoutValidationException(
                    "Cart changed during checkout. Please review your cart and try again.");
        }

        Cart cart = syncResult.cart();
        if (cart.isEmpty()) {
            log.warn("Checkout rejected for user {}: cart is empty after sync", userId.value());
            throw new OrderDomainException("Cannot place order from an empty cart.");
        }

        List<OrderLine> lines = cart.getItems().stream()
                .map(item -> OrderLine.fromCartItem(item, new OrderLineId(idGenerator.generateId())))
                .toList();

        Order order = Order.placeConfirmed(
                new OrderId(idGenerator.generateId()),
                userId,
                lines,
                shippingAddress
        );

        orderRepository.insert(order);
        cartCheckoutPort.clearAfterOrder(cart);

        log.debug(
                "Placed order {} for user {} (lines={}, total={})",
                order.getId().getValue(),
                userId.value(),
                order.lineCount(),
                order.getTotalAmount().getAmount()
        );
        return order;
    }

    @Transactional(readOnly = true)
    public Order getOrder(UserId userId, OrderId orderId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");

        log.debug("Fetching order {} for user {}", orderId.getValue(), userId.value());

        return orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> {
                    log.warn("Order {} not found for user {}", orderId.getValue(), userId.value());
                    return new OrderNotFoundException("Order not found: " + orderId.getValue());
                });
    }

    private static ShippingAddress toShippingAddress(PlaceOrderCommand command) {
        return new ShippingAddress(
                command.recipientName(),
                command.phone(),
                command.addressLine1(),
                command.addressLine2(),
                command.city(),
                command.postalCode(),
                command.countryCode()
        );
    }
}
