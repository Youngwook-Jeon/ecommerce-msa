package com.project.young.orderservice.messaging.consumer;

import com.project.young.kafka.saga.dto.CustomerRefundCompletedMessage;
import com.project.young.kafka.saga.dto.CustomerRefundFailedMessage;
import com.project.young.orderservice.application.dto.command.ApplyCustomerRefundResultCommand;
import com.project.young.orderservice.application.service.CustomerRefundApplicationService;
import com.project.young.orderservice.messaging.mapper.CustomerRefundResultMessageMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

class CustomerRefundResultListenerTest {

    @Test
    void completedEvent_updatesRefundBeforeAcknowledging() {
        CustomerRefundApplicationService refunds = mock(CustomerRefundApplicationService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant completedAt = Instant.now();
        new CustomerRefundResultListener(refunds, new CustomerRefundResultMessageMapper()).onCompleted(new CustomerRefundCompletedMessage(
                UUID.randomUUID(), UUID.randomUUID(), refundId, paymentId, orderId,
                "user-1", completedAt, Instant.now()), acknowledgment);

        InOrder ordered = inOrder(refunds, acknowledgment);
        ordered.verify(refunds).applyResult(new ApplyCustomerRefundResultCommand(
                refundId, paymentId, orderId, "user-1", true, null, 1, false, completedAt, null));
        ordered.verify(acknowledgment).acknowledge();
    }

    @Test
    void failedEvent_updatesRefundBeforeAcknowledging() {
        CustomerRefundApplicationService refunds = mock(CustomerRefundApplicationService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant failedAt = Instant.now();
        new CustomerRefundResultListener(refunds, new CustomerRefundResultMessageMapper()).onFailed(new CustomerRefundFailedMessage(
                UUID.randomUUID(), UUID.randomUUID(), refundId, paymentId, orderId,
                "user-1", "PSP refund failed", failedAt, Instant.now()), acknowledgment);

        InOrder ordered = inOrder(refunds, acknowledgment);
        ordered.verify(refunds).applyResult(new ApplyCustomerRefundResultCommand(
                refundId, paymentId, orderId, "user-1", false, "PSP refund failed", 2, false, null, failedAt));
        ordered.verify(acknowledgment).acknowledge();
    }

    @Test
    void invalidMetadata_doesNotApplyOrAcknowledge() {
        CustomerRefundApplicationService refunds = mock(CustomerRefundApplicationService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        CustomerRefundCompletedMessage message = new CustomerRefundCompletedMessage(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "user-1", Instant.now(), Instant.now(), 2, null);
        CustomerRefundResultListener listener = new CustomerRefundResultListener(
                refunds, new CustomerRefundResultMessageMapper());

        assertThatThrownBy(() -> listener.onCompleted(message, acknowledgment))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(refunds, acknowledgment);
    }

    @Test
    void applicationFailure_doesNotAcknowledge() {
        CustomerRefundApplicationService refunds = mock(CustomerRefundApplicationService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        doThrow(new IllegalStateException("Database unavailable")).when(refunds).applyResult(any());
        CustomerRefundFailedMessage message = new CustomerRefundFailedMessage(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "user-1", "PSP refund failed", Instant.now(), Instant.now());
        CustomerRefundResultListener listener = new CustomerRefundResultListener(
                refunds, new CustomerRefundResultMessageMapper());

        assertThatThrownBy(() -> listener.onFailed(message, acknowledgment))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(acknowledgment);
    }
}
