package com.project.young.paymentservice.web.controller;

import com.project.young.paymentservice.application.service.PaymentApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal reconciliation endpoint; it never executes a refund.
 */
@RestController
@RequestMapping("/internal/refund-compensations")
public class InternalRefundCompensationStatusController {

    private static final Logger log = LoggerFactory.getLogger(InternalRefundCompensationStatusController.class);

    private final PaymentApplicationService paymentApplicationService;

    public InternalRefundCompensationStatusController(PaymentApplicationService paymentApplicationService) {
        this.paymentApplicationService = paymentApplicationService;
    }

    @GetMapping("/{compensationEventId}")
    public ResponseEntity<Void> getStatus(@PathVariable UUID compensationEventId) {
        boolean processed = paymentApplicationService.isRefundCompensationProcessed(compensationEventId);
        log.debug("Refund compensation reconciliation lookup eventId={} processed={}", compensationEventId, processed);
        return processed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
