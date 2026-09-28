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
import com.project.young.orderservice.domain.exception.CustomerRefundStateConflictException;
import com.project.young.orderservice.domain.repository.CustomerRefundRepository;
import com.project.young.orderservice.domain.repository.OrderRepository;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.CustomerRefundId;
import com.project.young.orderservice.domain.valueobject.CustomerRefundStatus;
import com.project.young.orderservice.domain.valueobject.OrderStatus;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerRefundApplicationServiceTest {

    private static final UserId USER_ID = new UserId("customer-1");
    private static final UUID ORDER_ID_VALUE = UUID.randomUUID();
    private static final OrderId ORDER_ID = new OrderId(ORDER_ID_VALUE);
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID REFUND_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRefundRepository customerRefundRepository;

    @Mock
    private PaymentStatusQueryPort paymentStatusQueryPort;

    @Mock
    private CustomerRefundRequestedOutboxPort customerRefundRequestedOutboxPort;

    @Mock
    private CustomerRefundTxExecutor customerRefundTxExecutor;

    @Mock
    private IdGenerator idGenerator;

    @Mock
    private Order order;

    private CustomerRefundApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CustomerRefundApplicationService(
                orderRepository,
                customerRefundRepository,
                paymentStatusQueryPort,
                customerRefundRequestedOutboxPort,
                customerRefundTxExecutor,
                idGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(0);
            return action.get();
        }).when(customerRefundTxExecutor).executeInNewTransaction(any());
    }

    @Test
    void applyResult_completesRequestedRefundIdempotently() {
        CustomerRefund refund = CustomerRefund.request(new CustomerRefundId(REFUND_ID), ORDER_ID,
                PAYMENT_ID, USER_ID, "no longer needed", NOW.minusSeconds(10));
        when(customerRefundRepository.findByIdAndUserId(new CustomerRefundId(REFUND_ID), USER_ID))
                .thenReturn(Optional.of(refund));
        when(customerRefundRepository.updateIfRequested(refund)).thenReturn(true);
        ApplyCustomerRefundResultCommand result = new ApplyCustomerRefundResultCommand(
                REFUND_ID, PAYMENT_ID, ORDER_ID_VALUE, USER_ID.value(), true, null);

        assertThat(service.applyResult(result)).isTrue();
        assertThat(refund.getStatus()).isEqualTo(CustomerRefundStatus.COMPLETED);
        assertThat(service.applyResult(result)).isFalse();
        verify(customerRefundRepository).updateIfRequested(refund);
    }

    @Test
    void applyResult_marksFinalProviderFailure() {
        CustomerRefund refund = CustomerRefund.request(new CustomerRefundId(REFUND_ID), ORDER_ID,
                PAYMENT_ID, USER_ID, "no longer needed", NOW.minusSeconds(10));
        when(customerRefundRepository.findByIdAndUserId(new CustomerRefundId(REFUND_ID), USER_ID))
                .thenReturn(Optional.of(refund));
        when(customerRefundRepository.updateIfRequested(refund)).thenReturn(true);

        assertThat(service.applyResult(new ApplyCustomerRefundResultCommand(
                REFUND_ID, PAYMENT_ID, ORDER_ID_VALUE, USER_ID.value(), false, "PSP refund failed"))).isTrue();
        assertThat(refund.getStatus()).isEqualTo(CustomerRefundStatus.FAILED);
    }

    @Test
    void requestRefund_persistsRequestedRefundAndOutboxTogether() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(customerRefundRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentStatusQueryPort.findByOrderIds(java.util.List.of(ORDER_ID_VALUE))).thenReturn(Map.of(
                ORDER_ID_VALUE,
                new PaymentStatusSnapshot(PAYMENT_ID, ORDER_ID_VALUE, PaymentReconciliationStatus.COMPLETED, NOW)
        ));
        when(idGenerator.generateId()).thenReturn(REFUND_ID);

        CustomerRefundView result = service.requestRefund(USER_ID, command());

        assertThat(result.refundId()).isEqualTo(REFUND_ID);
        assertThat(result.status().name()).isEqualTo("REQUESTED");
        assertThat(result.reason()).isEqualTo("duplicate delivery charge");

        ArgumentCaptor<CustomerRefund> refundCaptor = ArgumentCaptor.forClass(CustomerRefund.class);
        verify(customerRefundRepository).insert(refundCaptor.capture());
        assertThat(refundCaptor.getValue().getPaymentId()).isEqualTo(PAYMENT_ID);

        ArgumentCaptor<CustomerRefundRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(CustomerRefundRequestedEvent.class);
        verify(customerRefundRequestedOutboxPort).enqueue(eventCaptor.capture());
        assertThat(eventCaptor.getValue().refundId()).isEqualTo(REFUND_ID);
        assertThat(eventCaptor.getValue().occurredAt()).isEqualTo(NOW);
        verify(customerRefundTxExecutor).executeInNewTransaction(any());
        verify(orderRepository, times(2)).findByIdAndUserId(ORDER_ID, USER_ID);
        verify(customerRefundRepository, times(2)).findByOrderId(ORDER_ID);
        InOrder transactionBoundaryOrder = inOrder(paymentStatusQueryPort, customerRefundTxExecutor);
        transactionBoundaryOrder.verify(paymentStatusQueryPort).findByOrderIds(java.util.List.of(ORDER_ID_VALUE));
        transactionBoundaryOrder.verify(customerRefundTxExecutor).executeInNewTransaction(any());
    }

    @Test
    void requestRefund_rejectsNonConfirmedOrderBeforeCreatingRecords() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(order.getStatus()).thenReturn(OrderStatus.PENDING_PAYMENT);

        assertThatThrownBy(() -> service.requestRefund(USER_ID, command()))
                .isInstanceOf(CustomerRefundStateConflictException.class)
                .hasMessage("Only a confirmed order can be refunded.");

        verify(customerRefundRepository, never()).insert(any());
        verify(customerRefundRequestedOutboxPort, never()).enqueue(any());
    }

    @Test
    void requestRefund_rejectsMissingCompletedPaymentBeforeCreatingRecords() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(customerRefundRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentStatusQueryPort.findByOrderIds(eq(java.util.List.of(ORDER_ID_VALUE)))).thenReturn(Map.of());

        assertThatThrownBy(() -> service.requestRefund(USER_ID, command()))
                .isInstanceOf(CustomerRefundStateConflictException.class)
                .hasMessage("A completed payment is required before requesting a refund.");

        verify(customerRefundRepository, never()).insert(any());
        verify(customerRefundRequestedOutboxPort, never()).enqueue(any());
    }

    private RequestCustomerRefundCommand command() {
        return new RequestCustomerRefundCommand(ORDER_ID_VALUE, "  duplicate delivery charge  ");
    }
}
