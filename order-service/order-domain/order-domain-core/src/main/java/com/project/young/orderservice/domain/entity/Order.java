package com.project.young.orderservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.orderservice.domain.exception.OrderDomainException;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.OrderStatus;
import com.project.young.orderservice.domain.valueobject.ShippingAddress;
import com.project.young.orderservice.domain.valueobject.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Order extends AggregateRoot<OrderId> {

    private static final Money FREE_SHIPPING = Money.ZERO;

    private final UserId userId;
    private final OrderStatus status;
    private final ShippingAddress shippingAddress;
    private final Money subtotalAmount;
    private final Money shippingAmount;
    private final Money totalAmount;
    private final List<OrderLine> lines;
    private Instant createdAt;
    private Instant updatedAt;

    private Order(Builder builder) {
        super.setId(builder.orderId);
        this.userId = builder.userId;
        this.status = builder.status;
        this.shippingAddress = builder.shippingAddress;
        this.lines = List.copyOf(builder.lines);
        this.subtotalAmount = builder.subtotalAmount;
        this.shippingAmount = builder.shippingAmount;
        this.totalAmount = builder.totalAmount;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public static Order placeConfirmed(
            OrderId orderId,
            UserId userId,
            List<OrderLine> lines,
            ShippingAddress shippingAddress
    ) {
        return place(orderId, userId, lines, shippingAddress, OrderStatus.CONFIRMED);
    }

    public static Order place(
            OrderId orderId,
            UserId userId,
            List<OrderLine> lines,
            ShippingAddress shippingAddress,
            OrderStatus status
    ) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(shippingAddress, "shippingAddress must not be null");
        Objects.requireNonNull(status, "status must not be null");

        if (lines == null || lines.isEmpty()) {
            throw new OrderDomainException("Order must contain at least one line.");
        }

        Money subtotal = lines.stream()
                .map(OrderLine::lineAmount)
                .reduce(Money.ZERO, Money::add);
        Money total = subtotal.add(FREE_SHIPPING);

        return builder()
                .orderId(orderId)
                .userId(userId)
                .status(status)
                .shippingAddress(shippingAddress)
                .lines(new ArrayList<>(lines))
                .subtotalAmount(subtotal)
                .shippingAmount(FREE_SHIPPING)
                .totalAmount(total)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int lineCount() {
        return lines.size();
    }

    public int totalQuantity() {
        return lines.stream().mapToInt(OrderLine::getQuantity).sum();
    }

    public UserId getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public ShippingAddress getShippingAddress() {
        return shippingAddress;
    }

    public Money getSubtotalAmount() {
        return subtotalAmount;
    }

    public Money getShippingAmount() {
        return shippingAmount;
    }

    public Money getTotalAmount() {
        return totalAmount;
    }

    public List<OrderLine> getLines() {
        return lines;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static final class Builder {
        private OrderId orderId;
        private UserId userId;
        private OrderStatus status;
        private ShippingAddress shippingAddress;
        private List<OrderLine> lines = List.of();
        private Money subtotalAmount;
        private Money shippingAmount;
        private Money totalAmount;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder orderId(OrderId orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder userId(UserId userId) {
            this.userId = userId;
            return this;
        }

        public Builder status(OrderStatus status) {
            this.status = status;
            return this;
        }

        public Builder shippingAddress(ShippingAddress shippingAddress) {
            this.shippingAddress = shippingAddress;
            return this;
        }

        public Builder lines(List<OrderLine> lines) {
            this.lines = lines;
            return this;
        }

        public Builder subtotalAmount(Money subtotalAmount) {
            this.subtotalAmount = subtotalAmount;
            return this;
        }

        public Builder shippingAmount(Money shippingAmount) {
            this.shippingAmount = shippingAmount;
            return this;
        }

        public Builder totalAmount(Money totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Order build() {
            return new Order(this);
        }
    }
}
