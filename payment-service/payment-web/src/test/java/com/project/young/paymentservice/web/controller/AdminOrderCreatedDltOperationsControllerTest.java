package com.project.young.paymentservice.web.controller;

import com.project.young.paymentservice.application.compensation.OrderCreatedDltStatus;
import com.project.young.paymentservice.application.dto.query.OrderCreatedDltOperationsView;
import com.project.young.paymentservice.application.service.OrderCreatedDltManualOperationService;
import com.project.young.paymentservice.application.service.OrderCreatedDltOperationsQueryService;
import com.project.young.paymentservice.web.config.SecurityConfig;
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

@WebMvcTest(AdminOrderCreatedDltOperationsController.class)
@Import(SecurityConfig.class)
@ContextConfiguration(classes = {AdminOrderCreatedDltOperationsController.class, SecurityConfig.class})
class AdminOrderCreatedDltOperationsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCreatedDltOperationsQueryService queryService;

    @MockitoBean
    private OrderCreatedDltManualOperationService manualOperationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(authorities = "ADMIN")
    void getItems_withAdminReturnsOperationalItems() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(queryService.getItems(OrderCreatedDltStatus.ESCALATED, 25)).thenReturn(List.of(new OrderCreatedDltOperationsView(
                eventId, UUID.randomUUID(), "user-1", "49.99", "USD", OrderCreatedDltStatus.ESCALATED, 5,
                "example.ProviderUnavailable", "provider unavailable", Instant.now(), null
        )));

        mockMvc.perform(get("/admin/operations/order-created-dlts")
                        .param("status", "ESCALATED").param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$[0].status").value("ESCALATED"));

        verify(queryService).getItems(OrderCreatedDltStatus.ESCALATED, 25);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void replay_withAdminReturnsNoContent() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(post("/admin/operations/order-created-dlts/{eventId}/replay", eventId))
                .andExpect(status().isNoContent());

        verify(manualOperationService).replay(eventId);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void resolve_withAdminRequiresReasonAndReturnsNoContent() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(post("/admin/operations/order-created-dlts/{eventId}/resolve", eventId)
                        .contentType("application/json")
                        .content("{\"reason\":\"duplicate order was handled\"}"))
                .andExpect(status().isNoContent());

        verify(manualOperationService).resolve(eventId, "duplicate order was handled");
    }
}
