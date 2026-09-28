package com.project.young.orderservice.application.service;

import com.project.young.common.application.contract.payment.PaymentReconciliationStatus;
import com.project.young.orderservice.application.dto.CustomerRefundView;
import com.project.young.orderservice.application.dto.PaymentStatusSnapshot;
import com.project.young.orderservice.application.dto.command.RequestCustomerRefundCommand;
import com.project.young.orderservice.application.dto.command.ApplyCustomerRefundResultCommand;
import com.project.young.orderservice.application.dto.event.CustomerRefundRequestedEvent;
import com.project.young.orderservice.application.port.output.CustomerRefundRequestedOutboxPort;
import com.project.young.orderservice.application.port.output.IdGenerator;
import com.project.young.orderservice.application.port.output.PaymentStatusQueryPort;
import com.project.young.orderservice.application.support.CustomerRefundTxExecutor;
import com.project.young.orderservice.domain.entity.CustomerRefund;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.exception.CustomerRefundNotFoundException;
import com.project.young.orderservice.domain.exception.CustomerRefundStateConflictException;
import com.project.young.orderservice.domain.exception.OrderNotFoundException;
import com.project.young.orderservice.domain.repository.CustomerRefundRepository;
import com.project.young.orderservice.domain.repository.OrderRepository;
import com.project.young.orderservice.domain.valueobject.CustomerRefundId;
import com.project.young.orderservice.domain.valueobject.CustomerRefundStatus;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.OrderStatus;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Creates and reads customer refund requests. The request record and outbox
 * entry are saved in one local transaction for Debezium CDC delivery.
 */
@Service
public class CustomerRefundApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CustomerRefundApplicationService.class);

    private final OrderRepository orderRepository;
    private final CustomerRefundRepository customerRefundRepository;
    private final PaymentStatusQueryPort paymentStatusQueryPort;
    private final CustomerRefundRequestedOutboxPort customerRefundRequestedOutboxPort;
    private final CustomerRefundTxExecutor customerRefundTxExecutor;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public CustomerRefundApplicationService(
            OrderRepository orderRepository,
            CustomerRefundRepository customerRefundRepository,
            PaymentStatusQueryPort paymentStatusQueryPort,
            CustomerRefundRequestedOutboxPort customerRefundRequestedOutboxPort,
            CustomerRefundTxExecutor customerRefundTxExecutor,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.customerRefundRepository = customerRefundRepository;
        this.paymentStatusQueryPort = paymentStatusQueryPort;
        this.customerRefundRequestedOutboxPort = customerRefundRequestedOutboxPort;
        this.customerRefundTxExecutor = customerRefundTxExecutor;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public CustomerRefundView requestRefund(UserId userId, RequestCustomerRefundCommand command) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.orderId(), "orderId must not be null");

        OrderId orderId = new OrderId(command.orderId());
        verifyRefundableOrderAndNoExistingRefund(userId, orderId);
        PaymentStatusSnapshot payment = findCompletedPayment(orderId);

        return customerRefundTxExecutor.executeInNewTransaction(
                () -> persistRequestedRefund(userId, command.reason(), orderId, payment));
    }

    private CustomerRefundView persistRequestedRefund(
            UserId userId,
            String reason,
            OrderId orderId,
            PaymentStatusSnapshot payment
    ) {
        // Repeat local checks after the out-of-transaction Payment-service call.
        // The unique order_id constraint remains the final concurrent-write guard.
        verifyRefundableOrderAndNoExistingRefund(userId, orderId);

        Instant requestedAt = clock.instant();
        CustomerRefund customerRefund = CustomerRefund.request(
                new CustomerRefundId(idGenerator.generateId()),
                orderId,
                payment.paymentId(),
                userId,
                reason,
                requestedAt
        );
        customerRefundRepository.insert(customerRefund);
        customerRefundRequestedOutboxPort.enqueue(new CustomerRefundRequestedEvent(
                customerRefund.getId().getValue(),
                customerRefund.getPaymentId(),
                customerRefund.getOrderId().getValue(),
                customerRefund.getUserId().value(),
                customerRefund.getReason(),
                requestedAt
        ));

        log.info(
                "Customer refund {} requested for order {} and payment {}",
                customerRefund.getId().getValue(),
                customerRefund.getOrderId().getValue(),
                customerRefund.getPaymentId()
        );
        return CustomerRefundView.from(customerRefund);
    }

    private void verifyRefundableOrderAndNoExistingRefund(UserId userId, OrderId orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> {
                    log.warn("Customer refund rejected: order {} not found for user {}", orderId.getValue(), userId.value());
                    return new OrderNotFoundException("Order not found: " + orderId.getValue());
                });

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            log.warn(
                    "Customer refund rejected: order {} is in status {} (user={})",
                    orderId.getValue(),
                    order.getStatus(),
                    userId.value()
            );
            throw new CustomerRefundStateConflictException("Only a confirmed order can be refunded.");
        }

        if (customerRefundRepository.findByOrderId(orderId).isPresent()) {
            log.warn("Customer refund rejected: refund already exists for order {}", orderId.getValue());
            throw new CustomerRefundStateConflictException("A customer refund already exists for this order.");
        }
    }

    private PaymentStatusSnapshot findCompletedPayment(OrderId orderId) {
        PaymentStatusSnapshot payment = paymentStatusQueryPort.findByOrderIds(List.of(orderId.getValue()))
                .get(orderId.getValue());
        if (payment == null || payment.status() != PaymentReconciliationStatus.COMPLETED) {
            log.warn("Customer refund rejected: captured payment not found for order {}", orderId.getValue());
            throw new CustomerRefundStateConflictException("A completed payment is required before requesting a refund.");
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public CustomerRefundView getRefund(UserId userId, CustomerRefundId refundId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(refundId, "refundId must not be null");

        log.debug("Fetching customer refund {} for user {}", refundId.getValue(), userId.value());
        return customerRefundRepository.findByIdAndUserId(refundId, userId)
                .map(CustomerRefundView::from)
                .orElseThrow(() -> new CustomerRefundNotFoundException(
                        "Customer refund not found: " + refundId.getValue()));
    }

    @Transactional
    public boolean applyResult(ApplyCustomerRefundResultCommand command) {
        CustomerRefund refund = customerRefundRepository.findByIdAndUserId(
                        new CustomerRefundId(command.refundId()), new UserId(command.userId()))
                .orElseThrow(() -> new CustomerRefundNotFoundException("Customer refund not found: " + command.refundId()));
        if (!refund.getPaymentId().equals(command.paymentId())
                || !refund.getOrderId().getValue().equals(command.orderId())) {
            throw new CustomerRefundStateConflictException("Customer refund result does not match request.");
        }
        CustomerRefundStatus target = command.succeeded()
                ? CustomerRefundStatus.COMPLETED : CustomerRefundStatus.FAILED;
        if (refund.getStatus() == target) {
            return false;
        }
        if (command.succeeded()) {
            refund.complete(clock.instant());
        } else {
            refund.fail(command.failureReason(), clock.instant());
        }
        if (!customerRefundRepository.updateIfRequested(refund)) {
            throw new CustomerRefundStateConflictException("Customer refund result raced with another transition.");
        }
        log.info("Applied customer refund result refundId={} paymentId={} status={}",
                command.refundId(), command.paymentId(), target);
        return true;
    }
}
