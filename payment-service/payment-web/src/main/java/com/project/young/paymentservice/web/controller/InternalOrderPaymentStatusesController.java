package com.project.young.paymentservice.web.controller;

import com.project.young.paymentservice.application.dto.query.OrderPaymentStatusView;
import com.project.young.paymentservice.application.service.PaymentApplicationService;
import com.project.young.paymentservice.web.dto.OrderPaymentStatusBatchRequest;
import com.project.young.paymentservice.web.dto.OrderPaymentStatusBatchResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Internal batch read API for Order's stale pending-payment reconciliation. */
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderPaymentStatusesController {

    private static final Logger log = LoggerFactory.getLogger(InternalOrderPaymentStatusesController.class);

    private final PaymentApplicationService paymentApplicationService;

    public InternalOrderPaymentStatusesController(PaymentApplicationService paymentApplicationService) {
        this.paymentApplicationService = paymentApplicationService;
    }

    @PostMapping("/payment-statuses")
    public ResponseEntity<OrderPaymentStatusBatchResponse> getPaymentStatuses(
            @Valid @RequestBody OrderPaymentStatusBatchRequest request
    ) {
        List<OrderPaymentStatusView> payments = paymentApplicationService
                .getPaymentStatusesByOrderIds(request.orderIds());
        log.debug("Internal batch payment status lookup requestedOrders={} foundPayments={}",
                request.orderIds().size(), payments.size());
        return ResponseEntity.ok(new OrderPaymentStatusBatchResponse(payments));
    }
}
