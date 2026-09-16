package com.project.young.paymentservice.web.controller;

import com.project.young.paymentservice.application.service.PaymentApplicationService;
import com.project.young.paymentservice.web.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalRefundCompensationStatusController.class)
@Import(SecurityConfig.class)
@ContextConfiguration(classes = {InternalRefundCompensationStatusController.class, SecurityConfig.class})
class InternalRefundCompensationStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentApplicationService paymentApplicationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void getStatus_allowsOnlyPaymentReconciliationServiceRole() throws Exception {
        UUID compensationEventId = UUID.randomUUID();
        when(paymentApplicationService.isRefundCompensationProcessed(compensationEventId)).thenReturn(true);

        mockMvc.perform(get("/internal/refund-compensations/{compensationEventId}", compensationEventId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/internal/refund-compensations/{compensationEventId}", compensationEventId)
                        .with(jwt().authorities(new SimpleGrantedAuthority(
                                SecurityConfig.INTERNAL_PAYMENT_RECONCILIATION_READ))))
                .andExpect(status().isNoContent());
    }
}
