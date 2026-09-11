package com.project.young.paymentservice.web.controller;

import com.project.young.paymentservice.application.dto.query.ProviderOperationEscalationsView;
import com.project.young.paymentservice.application.dto.query.ProviderSessionRequestEscalationView;
import com.project.young.paymentservice.application.service.PaymentOperationsQueryService;
import com.project.young.paymentservice.web.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminProviderOperationsController.class)
@Import(SecurityConfig.class)
@ContextConfiguration(classes = {AdminProviderOperationsController.class, SecurityConfig.class})
class AdminProviderOperationsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentOperationsQueryService paymentOperationsQueryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(authorities = "ADMIN")
    void getProviderEscalations_withAdmin_returnsOperationalQueues() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(paymentOperationsQueryService.getEscalated(25)).thenReturn(new ProviderOperationEscalationsView(
                List.of(new ProviderSessionRequestEscalationView(
                        paymentId, 5, "provider unavailable", Instant.now(), Instant.now())),
                List.of()
        ));

        mockMvc.perform(get("/admin/operations/provider-escalations").param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerSessionRequests[0].paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.providerSessionRequests[0].attempts").value(5))
                .andExpect(jsonPath("$.providerWebhookInboxItems").isEmpty());

        verify(paymentOperationsQueryService).getEscalated(25);
    }

    @Test
    @WithMockUser
    void getProviderEscalations_withoutAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/admin/operations/provider-escalations"))
                .andExpect(status().isForbidden());
    }
}
