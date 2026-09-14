package com.project.young.paymentservice.web.controller;

import com.project.young.common.application.contract.payment.PaymentReconciliationStatus;
import com.project.young.paymentservice.application.dto.query.OrderPaymentStatusView;
import com.project.young.paymentservice.application.service.PaymentApplicationService;
import com.project.young.paymentservice.web.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalOrderPaymentStatusesController.class)
@Import(SecurityConfig.class)
@ContextConfiguration(classes = {InternalOrderPaymentStatusesController.class, SecurityConfig.class})
class InternalOrderPaymentStatusesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentApplicationService paymentApplicationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void getPaymentStatuses_returnsMinimalPaymentProjections() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(paymentApplicationService.getPaymentStatusesByOrderIds(List.of(orderId))).thenReturn(List.of(
                new OrderPaymentStatusView(paymentId, orderId, PaymentReconciliationStatus.COMPLETED,
                        Instant.parse("2026-09-14T00:00:00Z"))
        ));

        mockMvc.perform(post("/internal/orders/payment-statuses")
                        .contentType("application/json")
                        .content("{\"orderIds\":[\"" + orderId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments[0].paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.payments[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.payments[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.payments[0].updatedAt").value("2026-09-14T00:00:00Z"));
    }

    @Test
    void getPaymentStatuses_rejectsMoreThanOneHundredOrderIds() throws Exception {
        String ids = java.util.stream.IntStream.range(0, 101)
                .mapToObj(ignored -> "\"" + UUID.randomUUID() + "\"")
                .collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(post("/internal/orders/payment-statuses")
                        .contentType("application/json")
                        .content("{\"orderIds\":[" + ids + "]}"))
                .andExpect(status().isBadRequest());
    }
}
