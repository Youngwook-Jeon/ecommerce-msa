package com.project.young.paymentservice.web.controller;

import com.project.young.paymentservice.application.exception.InvalidStripeWebhookException;
import com.project.young.paymentservice.application.port.input.StripeWebhookUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/stripe")
@ConditionalOnBean(StripeWebhookUseCase.class)
public class StripeWebhookController {

    public static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    private final StripeWebhookUseCase stripeWebhookUseCase;

    public StripeWebhookController(StripeWebhookUseCase stripeWebhookUseCase) {
        this.stripeWebhookUseCase = stripeWebhookUseCase;
    }

    @PostMapping
    public ResponseEntity<Void> handle(
            @RequestBody String payload,
            @RequestHeader(STRIPE_SIGNATURE_HEADER) String signatureHeader
    ) {
        stripeWebhookUseCase.handle(payload, signatureHeader);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(InvalidStripeWebhookException.class)
    public ResponseEntity<Void> handleInvalidWebhook(InvalidStripeWebhookException ignored) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
