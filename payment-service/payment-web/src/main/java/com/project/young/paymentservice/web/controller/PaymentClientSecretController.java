package com.project.young.paymentservice.web.controller;

import com.project.young.paymentservice.application.dto.query.ClientSecretView;
import com.project.young.paymentservice.application.service.PaymentApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentClientSecretController {

    private final PaymentApplicationService paymentApplicationService;

    public PaymentClientSecretController(PaymentApplicationService paymentApplicationService) {
        this.paymentApplicationService = paymentApplicationService;
    }

    /**
     * Returns the Stripe (or stub) client secret for Embedded Elements.
     * Frontend calls this after place-order while the payment is still PENDING.
     */
    @GetMapping("/orders/{orderId}/client-secret")
    public ResponseEntity<ClientSecretResponse> getClientSecret(@PathVariable UUID orderId) {
        ClientSecretView view = paymentApplicationService.getClientSecretByOrderId(orderId);
        return ResponseEntity.ok(new ClientSecretResponse(
                view.paymentId(),
                view.orderId(),
                view.provider(),
                view.clientSecret(),
                view.status()
        ));
    }

    public record ClientSecretResponse(
            UUID paymentId,
            UUID orderId,
            String provider,
            String clientSecret,
            String status
    ) {
    }
}
