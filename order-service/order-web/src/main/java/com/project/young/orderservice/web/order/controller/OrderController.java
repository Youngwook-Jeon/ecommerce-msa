package com.project.young.orderservice.web.order.controller;

import com.project.young.orderservice.application.dto.command.PlaceOrderCommand;
import com.project.young.orderservice.application.service.OrderApplicationService;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.UserId;
import com.project.young.orderservice.web.order.dto.OrderResponse;
import com.project.young.orderservice.web.order.mapper.OrderResponseMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;
    private final OrderResponseMapper orderResponseMapper;

    public OrderController(
            OrderApplicationService orderApplicationService,
            OrderResponseMapper orderResponseMapper
    ) {
        this.orderApplicationService = orderApplicationService;
        this.orderResponseMapper = orderResponseMapper;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PlaceOrderCommand command
    ) {
        log.info("A post request to place Order for user: {}", jwt.getSubject());
        UserId userId = new UserId(jwt.getSubject());
        Order order = orderApplicationService.placeOrder(userId, command);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderResponseMapper.toResponse(order));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId
    ) {
        log.info("A get request to get Order with id: {} for user: {}", orderId, jwt.getSubject());
        UserId userId = new UserId(jwt.getSubject());
        Order order = orderApplicationService.getOrder(userId, new OrderId(orderId));
        return ResponseEntity.ok(orderResponseMapper.toResponse(order));
    }

    @PostMapping("/{orderId}/confirm-payment")
    public ResponseEntity<OrderResponse> confirmPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId
    ) {
        log.info("A post request to confirm payment for order {} by user {}", orderId, jwt.getSubject());
        Order order = orderApplicationService.confirmPayment(
                new UserId(jwt.getSubject()),
                new OrderId(orderId)
        );
        return ResponseEntity.ok(orderResponseMapper.toResponse(order));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId
    ) {
        log.info("A post request to cancel order {} by user {}", orderId, jwt.getSubject());
        Order order = orderApplicationService.cancelOrder(
                new UserId(jwt.getSubject()),
                new OrderId(orderId)
        );
        return ResponseEntity.ok(orderResponseMapper.toResponse(order));
    }
}
