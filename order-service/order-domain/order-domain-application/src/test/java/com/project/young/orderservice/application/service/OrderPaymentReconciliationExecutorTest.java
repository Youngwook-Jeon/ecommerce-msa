package com.project.young.orderservice.application.service;

import com.project.young.common.application.contract.payment.PaymentReconciliationStatus;
import com.project.young.orderservice.application.dto.PendingPaymentOrderView;
import com.project.young.orderservice.application.dto.PaymentStatusSnapshot;
import com.project.young.orderservice.application.port.output.OrderPaymentReconciliationFailurePort;
import com.project.young.orderservice.application.port.output.PaymentStatusQueryPort;
import com.project.young.orderservice.application.port.output.PendingPaymentOrderQueryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderPaymentReconciliationExecutorTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    @Test
    void reconcileStalePendingPayments_confirmsCompletedPaymentAndClosesFailureRecord() {
        PendingPaymentOrderQueryPort pendingOrders = mock(PendingPaymentOrderQueryPort.class);
        PaymentStatusQueryPort paymentStatuses = mock(PaymentStatusQueryPort.class);
        OrderPaymentReconciliationFailurePort failures = mock(OrderPaymentReconciliationFailurePort.class);
        OrderApplicationService orders = mock(OrderApplicationService.class);
        PendingPaymentOrderView order = order();
        PaymentStatusSnapshot payment = payment(order.orderId(), PaymentReconciliationStatus.COMPLETED);
        when(pendingOrders.findUpdatedBefore(any(), anyInt())).thenReturn(List.of(order));
        when(failures.findEscalatedOrderIds(List.of(order.orderId()))).thenReturn(Set.of());
        when(paymentStatuses.findByOrderIds(List.of(order.orderId()))).thenReturn(Map.of(order.orderId(), payment));

        executor(pendingOrders, paymentStatuses, failures, orders).reconcileStalePendingPayments();

        verify(orders).confirmPayment(any(), any());
        verify(failures).resolve(order.orderId());
    }

    @Test
    void reconcileStalePendingPayments_recordsTerminalProcessingFailure() {
        PendingPaymentOrderQueryPort pendingOrders = mock(PendingPaymentOrderQueryPort.class);
        PaymentStatusQueryPort paymentStatuses = mock(PaymentStatusQueryPort.class);
        OrderPaymentReconciliationFailurePort failures = mock(OrderPaymentReconciliationFailurePort.class);
        OrderApplicationService orders = mock(OrderApplicationService.class);
        PendingPaymentOrderView order = order();
        PaymentStatusSnapshot payment = payment(order.orderId(), PaymentReconciliationStatus.FAILED);
        when(pendingOrders.findUpdatedBefore(any(), anyInt())).thenReturn(List.of(order));
        when(failures.findEscalatedOrderIds(List.of(order.orderId()))).thenReturn(Set.of());
        when(paymentStatuses.findByOrderIds(List.of(order.orderId()))).thenReturn(Map.of(order.orderId(), payment));
        org.mockito.Mockito.doThrow(new IllegalStateException("inventory unavailable"))
                .when(orders).cancelOrder(any(), any());

        executor(pendingOrders, paymentStatuses, failures, orders).reconcileStalePendingPayments();

        verify(failures).recordFailure(
                order.orderId(), order.userId(), payment.paymentId(), "FAILED", "inventory unavailable", NOW, 5);
    }

    @Test
    void reconcileStalePendingPayments_skipsEscalatedOrderBeforePaymentLookup() {
        PendingPaymentOrderQueryPort pendingOrders = mock(PendingPaymentOrderQueryPort.class);
        PaymentStatusQueryPort paymentStatuses = mock(PaymentStatusQueryPort.class);
        OrderPaymentReconciliationFailurePort failures = mock(OrderPaymentReconciliationFailurePort.class);
        PendingPaymentOrderView order = order();
        when(pendingOrders.findUpdatedBefore(any(), anyInt())).thenReturn(List.of(order));
        when(failures.findEscalatedOrderIds(List.of(order.orderId()))).thenReturn(Set.of(order.orderId()));

        executor(pendingOrders, paymentStatuses, failures, mock(OrderApplicationService.class)).reconcileStalePendingPayments();

        verify(paymentStatuses, never()).findByOrderIds(any());
    }

    private static OrderPaymentReconciliationExecutor executor(
            PendingPaymentOrderQueryPort pendingOrders,
            PaymentStatusQueryPort paymentStatuses,
            OrderPaymentReconciliationFailurePort failures,
            OrderApplicationService orders
    ) {
        return new OrderPaymentReconciliationExecutor(
                pendingOrders, paymentStatuses, failures, orders, Clock.fixed(NOW, ZoneOffset.UTC), 300_000, 5);
    }

    private static PendingPaymentOrderView order() {
        return new PendingPaymentOrderView(UUID.randomUUID(), "user-1", NOW.minusSeconds(600));
    }

    private static PaymentStatusSnapshot payment(UUID orderId, PaymentReconciliationStatus status) {
        return new PaymentStatusSnapshot(UUID.randomUUID(), orderId, status, NOW);
    }
}
