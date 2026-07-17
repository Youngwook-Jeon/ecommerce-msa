package com.project.young.orderservice.web.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.common.application.web.GlobalExceptionHandler;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.orderservice.application.service.OrderApplicationService;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.entity.OrderLine;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.OrderLineId;
import com.project.young.orderservice.domain.valueobject.OrderStatus;
import com.project.young.orderservice.domain.valueobject.ShippingAddress;
import com.project.young.orderservice.domain.valueobject.UserId;
import com.project.young.orderservice.web.config.SecurityConfig;
import com.project.young.orderservice.web.controller.TestConfig;
import com.project.young.orderservice.application.dto.command.PlaceOrderCommand;
import com.project.young.orderservice.web.exception.handler.OrderServiceGlobalExceptionHandler;
import com.project.young.orderservice.web.converter.OrderStatusWebConverter;
import com.project.young.orderservice.web.order.mapper.OrderResponseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({
        SecurityConfig.class,
        TestConfig.class,
        GlobalExceptionHandler.class,
        OrderServiceGlobalExceptionHandler.class,
        OrderResponseMapper.class,
        OrderStatusWebConverter.class
})
class OrderControllerTest {

    private static final String USER_SUBJECT = "018f0000-0000-7000-8000-000000000101";
    private static final UUID ORDER_ID = UUID.fromString("018f0000-0000-7000-8000-000000000501");
    private static final UUID LINE_ID = UUID.fromString("018f0000-0000-7000-8000-000000000502");
    private static final UUID PRODUCT_ID = UUID.fromString("018f0000-0000-7000-8000-000000000301");
    private static final UUID VARIANT_ID = UUID.fromString("018f0000-0000-7000-8000-000000000401");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderApplicationService orderApplicationService;

    @Test
    @DisplayName("POST /orders: 비인증 요청은 401")
    void placeOrder_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(placeOrderCommand())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /orders: 인증 사용자는 주문을 생성한다")
    void placeOrder_authenticated_returnsCreated() throws Exception {
        when(orderApplicationService.placeOrder(any(UserId.class), any(PlaceOrderCommand.class)))
                .thenReturn(sampleOrder());

        mockMvc.perform(post("/orders")
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(placeOrderCommand())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.lines[0].productName").value("Phone"));

        verify(orderApplicationService).placeOrder(eq(new UserId(USER_SUBJECT)), any(PlaceOrderCommand.class));
    }

    @Test
    @DisplayName("GET /orders/{orderId}: 인증 사용자는 자신의 주문을 조회한다")
    void getOrder_authenticated_returnsOrder() throws Exception {
        when(orderApplicationService.getOrder(new UserId(USER_SUBJECT), new OrderId(ORDER_ID)))
                .thenReturn(sampleOrder());

        mockMvc.perform(get("/orders/{orderId}", ORDER_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()));
    }

    @Test
    @DisplayName("POST /orders/{orderId}/confirm-payment: 결제 성공을 확정한다")
    void confirmPayment_authenticated_returnsConfirmedOrder() throws Exception {
        when(orderApplicationService.confirmPayment(
                new UserId(USER_SUBJECT),
                new OrderId(ORDER_ID)
        )).thenReturn(sampleOrder(OrderStatus.CONFIRMED));

        mockMvc.perform(post("/orders/{orderId}/confirm-payment", ORDER_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(orderApplicationService).confirmPayment(
                new UserId(USER_SUBJECT),
                new OrderId(ORDER_ID)
        );
    }

    @Test
    @DisplayName("POST /orders/{orderId}/cancel: 주문을 취소한다")
    void cancelOrder_authenticated_returnsCancelledOrder() throws Exception {
        when(orderApplicationService.cancelOrder(
                new UserId(USER_SUBJECT),
                new OrderId(ORDER_ID)
        )).thenReturn(sampleOrder(OrderStatus.CANCELLED));

        mockMvc.perform(post("/orders/{orderId}/cancel", ORDER_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(orderApplicationService).cancelOrder(
                new UserId(USER_SUBJECT),
                new OrderId(ORDER_ID)
        );
    }

    private PlaceOrderCommand placeOrderCommand() {
        return PlaceOrderCommand.builder()
                .recipientName("Kim Young")
                .phone("01012345678")
                .addressLine1("123 Main St")
                .city("Seoul")
                .postalCode("04524")
                .countryCode("KR")
                .build();
    }

    private Order sampleOrder() {
        return sampleOrder(OrderStatus.PENDING_PAYMENT);
    }

    private Order sampleOrder(OrderStatus status) {
        OrderLine line = OrderLine.reconstitute(
                new OrderLineId(LINE_ID),
                new com.project.young.common.domain.valueobject.ProductId(PRODUCT_ID),
                new com.project.young.common.domain.valueobject.ProductVariantId(VARIANT_ID),
                new CartItemSnapshot("Phone", "Acme", "SKU-1", null, new Money(new BigDecimal("100.00")), List.of()),
                1
        );

        return Order.builder()
                .orderId(new OrderId(ORDER_ID))
                .userId(new UserId(USER_SUBJECT))
                .status(status)
                .shippingAddress(new ShippingAddress(
                        "Kim Young", "01012345678", "123 Main St", null, "Seoul", "04524", "KR"))
                .lines(List.of(line))
                .subtotalAmount(new Money(new BigDecimal("100.00")))
                .shippingAmount(Money.ZERO)
                .totalAmount(new Money(new BigDecimal("100.00")))
                .build();
    }
}
