package com.project.young.paymentservice.web.controller;

import com.project.young.paymentservice.application.service.PaymentApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentRefundController {
    private static final Logger log = LoggerFactory.getLogger(InternalPaymentRefundController.class);
    private final PaymentApplicationService paymentApplicationService;

    public InternalPaymentRefundController(PaymentApplicationService paymentApplicationService) {
        this.paymentApplicationService = paymentApplicationService;
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<Void> refund(@PathVariable UUID paymentId, @RequestBody RefundRequest request) {
        log.info("Internal refund requested paymentId={} compensationEventId={}", paymentId, request.compensationEventId());
        paymentApplicationService.refundPayment(paymentId, request.compensationEventId());
        log.info("Internal refund completed paymentId={} compensationEventId={}", paymentId, request.compensationEventId());
        return ResponseEntity.noContent().build();
    }

    public record RefundRequest(UUID compensationEventId) {
    }
}
