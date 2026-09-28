package com.project.young.orderservice.messaging.consumer;

import com.project.young.kafka.saga.dto.CustomerRefundCompletedMessage;
import com.project.young.kafka.saga.dto.CustomerRefundFailedMessage;
import com.project.young.orderservice.application.dto.command.ApplyCustomerRefundResultCommand;
import com.project.young.orderservice.application.service.CustomerRefundApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CustomerRefundResultListenerTest {

    @Test
    void completedEvent_updatesRefundBeforeAcknowledging() {
        CustomerRefundApplicationService refunds = mock(CustomerRefundApplicationService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        new CustomerRefundResultListener(refunds).onCompleted(new CustomerRefundCompletedMessage(
                UUID.randomUUID(), UUID.randomUUID(), refundId, paymentId, orderId,
                "user-1", Instant.now(), Instant.now()), acknowledgment);

        verify(refunds).applyResult(new ApplyCustomerRefundResultCommand(
                refundId, paymentId, orderId, "user-1", true, null));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void failedEvent_updatesRefundBeforeAcknowledging() {
        CustomerRefundApplicationService refunds = mock(CustomerRefundApplicationService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        new CustomerRefundResultListener(refunds).onFailed(new CustomerRefundFailedMessage(
                UUID.randomUUID(), UUID.randomUUID(), refundId, paymentId, orderId,
                "user-1", "PSP refund failed", Instant.now(), Instant.now()), acknowledgment);

        verify(refunds).applyResult(new ApplyCustomerRefundResultCommand(
                refundId, paymentId, orderId, "user-1", false, "PSP refund failed"));
        verify(acknowledgment).acknowledge();
    }
}
