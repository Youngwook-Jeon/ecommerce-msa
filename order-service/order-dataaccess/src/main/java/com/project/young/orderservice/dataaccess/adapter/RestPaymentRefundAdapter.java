package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.port.output.PaymentRefundPort;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Component
public class RestPaymentRefundAdapter implements PaymentRefundPort {
    private static final Logger log = LoggerFactory.getLogger(RestPaymentRefundAdapter.class);
    private final RestClient paymentRefundRestClient;

    public RestPaymentRefundAdapter(RestClient paymentRefundRestClient) {
        this.paymentRefundRestClient = paymentRefundRestClient;
    }

    @Override
    public void refund(UUID paymentId, UUID compensationEventId) {
        log.info("Requesting payment refund paymentId={} compensationEventId={}", paymentId, compensationEventId);
        paymentRefundRestClient.post()
                .uri("/internal/payments/{paymentId}/refund", paymentId)
                .body(new RefundRequest(compensationEventId))
                .retrieve()
                .toBodilessEntity();
        log.info("Payment refund accepted paymentId={} compensationEventId={}", paymentId, compensationEventId);
    }

    private record RefundRequest(UUID compensationEventId) {
    }
}
