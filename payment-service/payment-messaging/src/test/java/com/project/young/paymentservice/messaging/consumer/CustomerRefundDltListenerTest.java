package com.project.young.paymentservice.messaging.consumer;

import com.project.young.paymentservice.application.dto.command.RecordCustomerRefundDltCommand;
import com.project.young.paymentservice.application.service.CustomerRefundDltApplicationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CustomerRefundDltListenerTest {
    private final CustomerRefundDltApplicationService queue = mock(CustomerRefundDltApplicationService.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final CustomerRefundDltListener listener = new CustomerRefundDltListener(queue);

    @Test
    void malformedPayload_isPersistedBeforeAck() {
        var record = new ConsumerRecord<String, String>("customer.refund.requested.DLT", 0, 42, "key", "not-json");
        listener.onDlt(record, ack, "customer.refund.requested", 1, 17L, "InvalidPayload", "invalid");
        var order = inOrder(queue, ack);
        order.verify(queue).record(new RecordCustomerRefundDltCommand(record.topic(), 0, 42, "key",
                "not-json", "customer.refund.requested", 1, 17L, "InvalidPayload", "invalid"));
        order.verify(ack).acknowledge();
    }

    @Test
    void storageFailure_doesNotAck() {
        doThrow(new IllegalStateException("db unavailable")).when(queue).record(any());
        assertThatThrownBy(() -> listener.onDlt(
                new ConsumerRecord<>("customer.refund.requested.DLT", 0, 42, null, null),
                ack, null, null, null, null, null)).isInstanceOf(IllegalStateException.class);
        verify(ack, never()).acknowledge();
    }
}
