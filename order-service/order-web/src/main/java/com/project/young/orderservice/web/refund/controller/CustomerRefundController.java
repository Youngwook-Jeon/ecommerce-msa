package com.project.young.orderservice.web.refund.controller;

import com.project.young.orderservice.application.dto.CustomerRefundView;
import com.project.young.orderservice.application.dto.command.RequestCustomerRefundCommand;
import com.project.young.orderservice.application.service.CustomerRefundApplicationService;
import com.project.young.orderservice.domain.valueobject.CustomerRefundId;
import com.project.young.orderservice.domain.valueobject.UserId;
import com.project.young.orderservice.web.refund.dto.CustomerRefundResponse;
import com.project.young.orderservice.web.refund.dto.RequestCustomerRefundRequest;
import com.project.young.orderservice.web.refund.mapper.CustomerRefundResponseMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping
public class CustomerRefundController {

    private final CustomerRefundApplicationService customerRefundApplicationService;
    private final CustomerRefundResponseMapper customerRefundResponseMapper;

    public CustomerRefundController(
            CustomerRefundApplicationService customerRefundApplicationService,
            CustomerRefundResponseMapper customerRefundResponseMapper
    ) {
        this.customerRefundApplicationService = customerRefundApplicationService;
        this.customerRefundResponseMapper = customerRefundResponseMapper;
    }

    @PostMapping("/orders/{orderId}/refunds")
    public ResponseEntity<CustomerRefundResponse> requestRefund(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId,
            @Valid @RequestBody RequestCustomerRefundRequest request
    ) {
        UserId userId = new UserId(jwt.getSubject());
        log.info("Customer refund requested for order {} by user {}", orderId, userId.value());

        CustomerRefundView customerRefund = customerRefundApplicationService.requestRefund(
                userId,
                new RequestCustomerRefundCommand(orderId, request.reason())
        );
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/refunds/{refundId}")
                .buildAndExpand(customerRefund.refundId())
                .toUri();
        return ResponseEntity.accepted()
                .location(location)
                .body(customerRefundResponseMapper.toResponse(customerRefund));
    }

    @GetMapping("/refunds/{refundId}")
    public ResponseEntity<CustomerRefundResponse> getRefund(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID refundId
    ) {
        UserId userId = new UserId(jwt.getSubject());
        log.info("Customer refund {} requested by user {}", refundId, userId.value());

        CustomerRefundView customerRefund = customerRefundApplicationService.getRefund(
                userId,
                new CustomerRefundId(refundId)
        );
        return ResponseEntity.ok(customerRefundResponseMapper.toResponse(customerRefund));
    }
}
