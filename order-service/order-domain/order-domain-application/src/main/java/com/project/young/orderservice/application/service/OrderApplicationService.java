package com.project.young.orderservice.application.service;

import com.project.young.orderservice.application.dto.command.PlaceOrderCommand;
import com.project.young.orderservice.application.port.output.CartCheckoutPort;
import com.project.young.orderservice.application.port.output.IdGenerator;
import com.project.young.orderservice.application.port.output.InventoryReservationClientException;
import com.project.young.orderservice.application.port.output.InventoryReservationConflictException;
import com.project.young.orderservice.application.port.output.InventoryReservationPort;
import com.project.young.orderservice.application.port.output.view.ReserveInventoryLineResultView;
import com.project.young.orderservice.application.port.output.view.ReserveInventoryLineView;
import com.project.young.orderservice.application.port.output.view.ReserveInventoryResultView;
import com.project.young.orderservice.application.support.OrderPlacementTxExecutor;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.entity.OrderLine;
import com.project.young.orderservice.domain.exception.OrderCheckoutValidationException;
import com.project.young.orderservice.domain.exception.OrderDomainException;
import com.project.young.orderservice.domain.exception.OrderNotFoundException;
import com.project.young.orderservice.domain.exception.OrderStateConflictException;
import com.project.young.orderservice.domain.repository.OrderRepository;
import com.project.young.orderservice.domain.sync.CartSyncResult;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.OrderLineId;
import com.project.young.orderservice.domain.valueobject.OrderStatus;
import com.project.young.orderservice.domain.valueobject.ShippingAddress;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Checkout orchestration for order placement.
 * <p>
 * Local DB work and external inventory calls are intentionally separated:
 * cart sync and order insert each run in their own transactions, while
 * {@code reserve}/{@code release} stay outside those boundaries. Cart is not
 * cleared here — clearing is deferred until payment succeeds.
 */
@Service
public class OrderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);
    private static final String ACTIVE_RESERVATION_STATUS = "ACTIVE";
    /** Allows local clock to be slightly ahead of product-service without rejecting a valid hold. */
    private static final Duration RESERVE_EXPIRES_AT_CLOCK_SKEW = Duration.ofSeconds(30);

    private final OrderRepository orderRepository;
    private final CartCheckoutPort cartCheckoutPort;
    private final InventoryReservationPort inventoryReservationPort;
    private final OrderPlacementTxExecutor orderPlacementTxExecutor;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public OrderApplicationService(
            OrderRepository orderRepository,
            CartCheckoutPort cartCheckoutPort,
            InventoryReservationPort inventoryReservationPort,
            OrderPlacementTxExecutor orderPlacementTxExecutor,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.cartCheckoutPort = cartCheckoutPort;
        this.inventoryReservationPort = inventoryReservationPort;
        this.orderPlacementTxExecutor = orderPlacementTxExecutor;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * Places a {@code PENDING_PAYMENT} order after soft-holding inventory.
     * Not transactional: orchestration only. Cart remains until payment confirmation.
     */
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

        OrderId orderId = new OrderId(idGenerator.generateId());
        List<ReserveInventoryLineView> reserveLines = toReserveLines(cart);
        reserveInventoryForCheckout(orderId, reserveLines);

        List<OrderLine> lines = cart.getItems().stream()
                .map(item -> OrderLine.fromCartItem(item, new OrderLineId(idGenerator.generateId())))
                .toList();

        Order order = Order.placePendingPayment(orderId, userId, lines, shippingAddress);

        try {
            // REQUIRES_NEW so commit completes (or fails) before we return — catch covers commit failures.
            orderPlacementTxExecutor.runInNewTransaction(() -> orderRepository.insert(order));
        } catch (RuntimeException persistFailure) {
            compensateInventoryRelease(orderId, persistFailure);
            throw persistFailure;
        }

        log.debug(
                "Placed order {} for user {} (lines={}, total={})",
                order.getId().getValue(),
                userId.value(),
                order.lineCount(),
                order.getTotalAmount().getAmount()
        );
        return order;
    }

    /**
     * Stub payment-success flow. Inventory confirmation is idempotent; after it succeeds,
     * the local status transition commits in a separate transaction. A retry can complete
     * a prior partial attempt. Cart clearing is last and preserves a cart changed meanwhile.
     */
    public Order confirmPayment(UserId userId, OrderId orderId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");

        Order current = requireOrder(userId, orderId);
        if (current.getStatus() == OrderStatus.CONFIRMED) {
            cartCheckoutPort.clearAfterPayment(current);
            return current;
        }
        if (current.getStatus() != OrderStatus.PENDING_PAYMENT) {
            current.confirmPayment(); // raises the domain transition error
        }

        inventoryReservationPort.confirm(orderId.getValue());

        Order confirmed = orderPlacementTxExecutor.executeInNewTransaction(() -> {
            Order order = requireOrder(userId, orderId);
            if (order.getStatus() == OrderStatus.CONFIRMED) {
                return order;
            }
            order.confirmPayment();
            if (!orderRepository.updateStatus(order, OrderStatus.PENDING_PAYMENT)) {
                throw new OrderStateConflictException(
                        "Order status changed concurrently. Please retry payment confirmation.");
            }
            return order;
        });

        cartCheckoutPort.clearAfterPayment(confirmed);
        return confirmed;
    }

    /**
     * Stub payment-cancel flow. Persist cancellation first so a release outage cannot leave
     * an order payable; release remains retryable and the soft hold also expires by TTL.
     */
    public Order cancelOrder(UserId userId, OrderId orderId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");

        Order cancelled = orderPlacementTxExecutor.executeInNewTransaction(() -> {
            Order order = requireOrder(userId, orderId);
            if (order.getStatus() == OrderStatus.CANCELLED) {
                return order;
            }
            order.cancel();
            if (!orderRepository.updateStatus(order, OrderStatus.PENDING_PAYMENT)) {
                throw new OrderStateConflictException(
                        "Order status changed concurrently. Please retry cancellation.");
            }
            return order;
        });

        inventoryReservationPort.release(orderId.getValue());
        return cancelled;
    }

    @Transactional(readOnly = true)
    public Order getOrder(UserId userId, OrderId orderId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");

        log.debug("Fetching order {} for user {}", orderId.getValue(), userId.value());

        return requireOrder(userId, orderId);
    }

    private Order requireOrder(UserId userId, OrderId orderId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> {
                    log.warn("Order {} not found for user {}", orderId.getValue(), userId.value());
                    return new OrderNotFoundException("Order not found: " + orderId.getValue());
                });
    }

    private void reserveInventoryForCheckout(OrderId orderId, List<ReserveInventoryLineView> reserveLines) {
        ReserveInventoryResultView result;
        try {
            result = inventoryReservationPort.reserve(orderId.getValue(), reserveLines);
        } catch (InventoryReservationConflictException conflict) {
            log.warn(
                    "Checkout rejected for order {}: inventory reserve conflict ({})",
                    orderId.getValue(),
                    conflict.getMessage()
            );
            throw new OrderCheckoutValidationException(
                    "Insufficient inventory for one or more items. Please review your cart and try again.");
        }

        try {
            validateReserveResult(orderId, reserveLines, result);
        } catch (InventoryReservationClientException invalidResponse) {
            log.warn(
                    "Invalid reserve response for order {}; releasing inventory reservation ({})",
                    orderId.getValue(),
                    invalidResponse.getMessage()
            );
            compensateInventoryRelease(orderId, invalidResponse);
            throw invalidResponse;
        }
    }

    private void validateReserveResult(
            OrderId orderId,
            List<ReserveInventoryLineView> requested,
            ReserveInventoryResultView result
    ) {
        if (result == null) {
            throw new InventoryReservationClientException(
                    "Product inventory returned an empty reserve response.");
        }
        if (!orderId.getValue().equals(result.checkoutId())) {
            throw new InventoryReservationClientException(
                    "Product inventory reserve response checkoutId does not match orderId.");
        }
        Instant expiresAt = result.expiresAt();
        Instant earliestAcceptableExpiry = clock.instant().minus(RESERVE_EXPIRES_AT_CLOCK_SKEW);
        if (expiresAt == null || !expiresAt.isAfter(earliestAcceptableExpiry)) {
            throw new InventoryReservationClientException(
                    "Product inventory reserve response has a missing or expired expiresAt.");
        }

        List<ReserveInventoryLineResultView> resultLines = result.lines();
        if (resultLines == null) {
            throw new InventoryReservationClientException(
                    "Product inventory reserve response lines must not be null.");
        }

        Map<UUID, Integer> requestedByVariant = new HashMap<>();
        for (ReserveInventoryLineView line : requested) {
            requestedByVariant.merge(line.productVariantId(), line.quantity(), Integer::sum);
        }

        Map<UUID, Integer> reservedByVariant = new HashMap<>();
        for (ReserveInventoryLineResultView line : resultLines) {
            if (line.reservationId() == null) {
                throw new InventoryReservationClientException(
                        "Product inventory reserve response contains a line without reservationId.");
            }
            if (line.productVariantId() == null) {
                throw new InventoryReservationClientException(
                        "Product inventory reserve response contains a line without productVariantId.");
            }
            if (!ACTIVE_RESERVATION_STATUS.equals(line.status())) {
                throw new InventoryReservationClientException(
                        "Product inventory reserve response contains a non-ACTIVE line status: "
                                + line.status());
            }
            if (line.quantity() <= 0) {
                throw new InventoryReservationClientException(
                        "Product inventory reserve response contains a non-positive quantity.");
            }
            reservedByVariant.merge(line.productVariantId(), line.quantity(), Integer::sum);
        }

        if (!requestedByVariant.equals(reservedByVariant)) {
            throw new InventoryReservationClientException(
                    "Product inventory reserve response lines do not match the requested variants/quantities.");
        }
    }

    private void compensateInventoryRelease(OrderId orderId, RuntimeException cause) {
        log.warn(
                "Releasing inventory reservation for order {} after failure: {}",
                orderId.getValue(),
                cause.getMessage(),
                cause
        );
        try {
            inventoryReservationPort.release(orderId.getValue());
        } catch (RuntimeException releaseFailure) {
            log.error(
                    "Failed to release inventory reservation for order {} after failure",
                    orderId.getValue(),
                    releaseFailure
            );
        }
    }

    private static List<ReserveInventoryLineView> toReserveLines(Cart cart) {
        return cart.getItems().stream()
                .map(item -> new ReserveInventoryLineView(
                        item.getProductVariantId().getValue(),
                        item.getQuantity()))
                .toList();
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
