package com.project.young.paymentservice.web.controller;

import com.project.young.paymentservice.application.dto.query.ProviderOperationEscalationsView;
import com.project.young.paymentservice.application.service.PaymentOperationsQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/operations")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminProviderOperationsController {

    private static final Logger log = LoggerFactory.getLogger(AdminProviderOperationsController.class);

    private final PaymentOperationsQueryService paymentOperationsQueryService;

    public AdminProviderOperationsController(PaymentOperationsQueryService paymentOperationsQueryService) {
        this.paymentOperationsQueryService = paymentOperationsQueryService;
    }

    @GetMapping("/provider-escalations")
    public ResponseEntity<ProviderOperationEscalationsView> getProviderEscalations(
            @RequestParam(defaultValue = "100") int limit
    ) {
        ProviderOperationEscalationsView escalations = paymentOperationsQueryService.getEscalated(limit);
        log.info(
                "Admin provider escalation lookup sessionRequestCount={} webhookInboxCount={}",
                escalations.providerSessionRequests().size(),
                escalations.providerWebhookInboxItems().size()
        );
        return ResponseEntity.ok(escalations);
    }
}
