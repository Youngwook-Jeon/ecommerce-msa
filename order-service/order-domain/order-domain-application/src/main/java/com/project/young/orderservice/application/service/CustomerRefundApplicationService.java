package com.project.young.orderservice.application.service;

import com.project.young.common.application.contract.payment.PaymentReconciliationStatus;
import com.project.young.orderservice.application.dto.CustomerRefundView;
import com.project.young.orderservice.application.dto.PaymentStatusSnapshot;
import com.project.young.orderservice.application.dto.command.RequestCustomerRefundCommand;
import com.project.young.orderservice.application.dto.event.CustomerRefundRequestedEvent;
import com.project.young.orderservice.application.port.output.CustomerRefundRequestedOutboxPort;
import com.project.young.orderservice.application.port.output.IdGenerator;
import com.project.young.orderservice.application.port.output.PaymentStatusQueryPort;
import com.project.young.orderservice.domain.entity.CustomerRefund;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.exception.CustomerRefundDomainException;
import com.project.young.orderservice.domain.exception.CustomerRefundStateConflictException;
import com.project.young.orderservice.domain.exception.OrderNotFoundException;
import com.project.young.orderservice.domain.repository.CustomerRefundRepository;
import com.project.young.orderservice.domain.repository.OrderRepository;
import com.project.young.orderservice.domain.valueobject.CustomerRefundId;
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
    private final IdGenerator idGenerator;
    private final Clock clock;

    public CustomerRefundApplicationService(
            OrderRepository orderRepository,
            CustomerRefundRepository customerRefundRepository,
            PaymentStatusQueryPort paymentStatusQueryPort,
            CustomerRefundRequestedOutboxPort customerRefundRequestedOutboxPort,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.customerRefundRepository = customerRefundRepository;
        this.paymentStatusQueryPort = paymentStatusQueryPort;
        this.customerRefundRequestedOutboxPort = customerRefundRequestedOutboxPort;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public CustomerRefundView requestRefund(UserId userId, RequestCustomerRefundCommand command) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.orderId(), "orderId must not be null");

        OrderId orderId = new OrderId(command.orderId());
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
            throw new CustomerRefundStateConflictException(
                    "Only a confirmed order can be refunded.");
        }

        if (customerRefundRepository.findByOrderId(orderId).isPresent()) {
            log.warn("Customer refund rejected: refund already exists for order {}", orderId.getValue());
            throw new CustomerRefundStateConflictException("A customer refund already exists for this order.");
        }

        PaymentStatusSnapshot payment = paymentStatusQueryPort.findByOrderIds(List.of(orderId.getValue()))
                .get(orderId.getValue());
        if (payment == null || payment.status() != PaymentReconciliationStatus.COMPLETED) {
            log.warn("Customer refund rejected: captured payment not found for order {}", orderId.getValue());
            throw new CustomerRefundStateConflictException("A completed payment is required before requesting a refund.");
        }

        Instant requestedAt = clock.instant();
        CustomerRefund customerRefund = CustomerRefund.request(
                new CustomerRefundId(idGenerator.generateId()),
                orderId,
                payment.paymentId(),
                userId,
                command.reason(),
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

    @Transactional(readOnly = true)
    public CustomerRefundView getRefund(UserId userId, CustomerRefundId refundId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(refundId, "refundId must not be null");

        log.debug("Fetching customer refund {} for user {}", refundId.getValue(), userId.value());
        return customerRefundRepository.findByIdAndUserId(refundId, userId)
                .map(CustomerRefundView::from)
                .orElseThrow(() -> new CustomerRefundDomainException("Customer refund not found: " + refundId.getValue()));
    }
}
