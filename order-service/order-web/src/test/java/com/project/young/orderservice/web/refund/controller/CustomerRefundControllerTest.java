package com.project.young.orderservice.web.refund.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.common.application.web.GlobalExceptionHandler;
import com.project.young.orderservice.application.dto.CustomerRefundView;
import com.project.young.orderservice.application.dto.command.RequestCustomerRefundCommand;
import com.project.young.orderservice.application.service.CustomerRefundApplicationService;
import com.project.young.orderservice.domain.valueobject.CustomerRefundId;
import com.project.young.orderservice.domain.valueobject.CustomerRefundStatus;
import com.project.young.orderservice.domain.valueobject.UserId;
import com.project.young.orderservice.web.config.SecurityConfig;
import com.project.young.orderservice.web.controller.TestConfig;
import com.project.young.orderservice.web.exception.handler.OrderServiceGlobalExceptionHandler;
import com.project.young.orderservice.web.refund.mapper.CustomerRefundResponseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerRefundController.class)
@Import({
        SecurityConfig.class,
        TestConfig.class,
        GlobalExceptionHandler.class,
        OrderServiceGlobalExceptionHandler.class,
        CustomerRefundResponseMapper.class
})
class CustomerRefundControllerTest {

    private static final String USER_SUBJECT = "018f0000-0000-7000-8000-000000000101";
    private static final UUID ORDER_ID = UUID.fromString("018f0000-0000-7000-8000-000000000501");
    private static final UUID PAYMENT_ID = UUID.fromString("018f0000-0000-7000-8000-000000000601");
    private static final UUID REFUND_ID = UUID.fromString("018f0000-0000-7000-8000-000000000701");
    private static final Instant REQUESTED_AT = Instant.parse("2026-09-27T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerRefundApplicationService customerRefundApplicationService;

    @Test
    @DisplayName("POST /orders/{orderId}/refunds: 인증 사용자의 환불 요청을 비동기로 접수한다")
    void requestRefund_authenticated_returnsAccepted() throws Exception {
        when(customerRefundApplicationService.requestRefund(any(UserId.class), any(RequestCustomerRefundCommand.class)))
                .thenReturn(customerRefundView());

        mockMvc.perform(post("/orders/{orderId}/refunds", ORDER_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestBody("duplicate delivery charge"))))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "http://localhost/refunds/" + REFUND_ID))
                .andExpect(jsonPath("$.refundId").value(REFUND_ID.toString()))
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        verify(customerRefundApplicationService).requestRefund(
                eq(new UserId(USER_SUBJECT)),
                eq(new RequestCustomerRefundCommand(ORDER_ID, "duplicate delivery charge"))
        );
    }

    @Test
    @DisplayName("POST /orders/{orderId}/refunds: 빈 환불 사유는 400")
    void requestRefund_blankReason_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/orders/{orderId}/refunds", ORDER_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /refunds/{refundId}: 인증 사용자는 자신의 환불을 조회한다")
    void getRefund_authenticated_returnsRefund() throws Exception {
        when(customerRefundApplicationService.getRefund(
                new UserId(USER_SUBJECT),
                new CustomerRefundId(REFUND_ID)
        )).thenReturn(customerRefundView());

        mockMvc.perform(get("/refunds/{refundId}", REFUND_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_SUBJECT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value(REFUND_ID.toString()))
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.reason").value("duplicate delivery charge"));
    }

    @Test
    @DisplayName("POST /orders/{orderId}/refunds: 비인증 요청은 401")
    void requestRefund_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/orders/{orderId}/refunds", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"duplicate delivery charge\"}"))
                .andExpect(status().isUnauthorized());
    }

    private CustomerRefundView customerRefundView() {
        return new CustomerRefundView(
                REFUND_ID,
                ORDER_ID,
                PAYMENT_ID,
                USER_SUBJECT,
                "duplicate delivery charge",
                CustomerRefundStatus.REQUESTED,
                null,
                REQUESTED_AT,
                REQUESTED_AT
        );
    }

    private record RequestBody(String reason) {
    }
}
