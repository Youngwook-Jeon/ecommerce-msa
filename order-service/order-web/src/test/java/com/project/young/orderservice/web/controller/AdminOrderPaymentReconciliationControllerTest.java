package com.project.young.orderservice.web.controller;

import com.project.young.orderservice.application.dto.OrderPaymentReconciliationEscalationView;
import com.project.young.orderservice.application.service.OrderPaymentReconciliationOperationsQueryService;
import com.project.young.orderservice.application.service.OrderPaymentReconciliationOperationsService;
import com.project.young.orderservice.web.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminOrderPaymentReconciliationController.class)
@Import(SecurityConfig.class)
@ContextConfiguration(classes = {AdminOrderPaymentReconciliationController.class, SecurityConfig.class})
class AdminOrderPaymentReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderPaymentReconciliationOperationsQueryService queryService;

    @MockitoBean
    private OrderPaymentReconciliationOperationsService operationsService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(authorities = "ADMIN")
    void getEscalated_withAdminReturnsOperationalItems() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(queryService.getEscalated(25)).thenReturn(List.of(new OrderPaymentReconciliationEscalationView(
                orderId, "user-1", UUID.randomUUID(), "COMPLETED", 5, "inventory unavailable",
                Instant.now(), Instant.now(), com.project.young.orderservice.application.reconciliation.OrderPaymentReconciliationStatus.ESCALATED,
                null, null, null
        )));

        mockMvc.perform(get("/admin/operations/payment-reconciliation-escalations").param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$[0].attempts").value(5));

        verify(queryService).getEscalated(25);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void requestRefund_withAdminReturnsAcceptedCompensationId() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID compensationEventId = UUID.randomUUID();
        when(operationsService.requestRefund(orderId, "order cannot be confirmed"))
                .thenReturn(compensationEventId);

        mockMvc.perform(post("/admin/operations/payment-reconciliation-escalations/{orderId}/refund", orderId)
                        .contentType("application/json")
                        .content("{\"reason\":\"order cannot be confirmed\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.compensationEventId").value(compensationEventId.toString()));

        verify(operationsService).requestRefund(orderId, "order cannot be confirmed");
    }
}
