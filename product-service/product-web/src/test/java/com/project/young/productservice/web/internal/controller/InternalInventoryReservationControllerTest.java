package com.project.young.productservice.web.internal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.common.application.web.GlobalExceptionHandler;
import com.project.young.productservice.application.dto.command.ReserveInventoryCommand;
import com.project.young.productservice.application.dto.result.ReserveInventoryResult;
import com.project.young.productservice.application.service.InventoryReservationApplicationService;
import com.project.young.productservice.domain.exception.InsufficientInventoryException;
import com.project.young.productservice.domain.exception.InventoryDomainException;
import com.project.young.productservice.domain.exception.InventoryReservationNotFoundException;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.controller.TestConfig;
import com.project.young.productservice.web.exception.handler.ProductServiceGlobalExceptionHandler;
import com.project.young.productservice.web.internal.dto.ReserveInventoryRequest;
import com.project.young.productservice.web.internal.mapper.InventoryReservationWebMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalInventoryReservationController.class)
@Import({
        SecurityConfig.class,
        TestConfig.class,
        GlobalExceptionHandler.class,
        ProductServiceGlobalExceptionHandler.class,
        InventoryReservationWebMapper.class
})
class InternalInventoryReservationControllerTest {

    private static final UUID CHECKOUT_ID = UUID.fromString("018f0000-0000-7000-8000-000000000301");
    private static final UUID VARIANT_ID = UUID.fromString("018f0000-0000-7000-8000-000000000302");
    private static final UUID RESERVATION_ID = UUID.fromString("018f0000-0000-7000-8000-000000000303");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InventoryReservationApplicationService inventoryReservationApplicationService;

    @Test
    @WithMockUser
    @DisplayName("POST /internal/inventory/reservations: 예약을 생성하고 201을 반환한다")
    void reserve_success() throws Exception {
        Instant expiresAt = Instant.parse("2026-07-15T10:15:00Z");
        when(inventoryReservationApplicationService.reserve(any(ReserveInventoryCommand.class)))
                .thenReturn(new ReserveInventoryResult(
                        CHECKOUT_ID,
                        expiresAt,
                        List.of(new ReserveInventoryResult.Line(RESERVATION_ID, VARIANT_ID, 2, "ACTIVE")),
                        false
                ));

        ReserveInventoryRequest request = new ReserveInventoryRequest(
                CHECKOUT_ID,
                List.of(new ReserveInventoryRequest.Line(VARIANT_ID, 2))
        );

        mockMvc.perform(post("/internal/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkoutId").value(CHECKOUT_ID.toString()))
                .andExpect(jsonPath("$.expiresAt").value("2026-07-15T10:15:00Z"))
                .andExpect(jsonPath("$.reusedExisting").value(false))
                .andExpect(jsonPath("$.lines[0].reservationId").value(RESERVATION_ID.toString()))
                .andExpect(jsonPath("$.lines[0].productVariantId").value(VARIANT_ID.toString()))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].status").value("ACTIVE"));

        verify(inventoryReservationApplicationService).reserve(any(ReserveInventoryCommand.class));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /internal/inventory/reservations: lines가 비어 있으면 400")
    void reserve_emptyLines_returnsBadRequest() throws Exception {
        String body = """
                {
                  "checkoutId": "%s",
                  "lines": []
                }
                """.formatted(CHECKOUT_ID);

        mockMvc.perform(post("/internal/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /internal/inventory/reservations: 재고 부족이면 409")
    void reserve_insufficientInventory_returnsConflict() throws Exception {
        when(inventoryReservationApplicationService.reserve(any(ReserveInventoryCommand.class)))
                .thenThrow(new InsufficientInventoryException("Insufficient inventory for variant " + VARIANT_ID));

        ReserveInventoryRequest request = new ReserveInventoryRequest(
                CHECKOUT_ID,
                List.of(new ReserveInventoryRequest.Line(VARIANT_ID, 5))
        );

        mockMvc.perform(post("/internal/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Insufficient inventory for variant " + VARIANT_ID));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /internal/inventory/reservations: 도메인 예외면 400")
    void reserve_domainException_returnsBadRequest() throws Exception {
        when(inventoryReservationApplicationService.reserve(any(ReserveInventoryCommand.class)))
                .thenThrow(new InventoryDomainException("Product variant is not reservable"));

        ReserveInventoryRequest request = new ReserveInventoryRequest(
                CHECKOUT_ID,
                List.of(new ReserveInventoryRequest.Line(VARIANT_ID, 1))
        );

        mockMvc.perform(post("/internal/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Product variant is not reservable"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /internal/inventory/reservations/{checkoutId}/confirm: 확정 후 204")
    void confirm_success() throws Exception {
        mockMvc.perform(post("/internal/inventory/reservations/{checkoutId}/confirm", CHECKOUT_ID))
                .andExpect(status().isNoContent());

        verify(inventoryReservationApplicationService).confirm(eq(CHECKOUT_ID));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /internal/inventory/reservations/{checkoutId}/confirm: 예약 없으면 404")
    void confirm_notFound_returnsNotFound() throws Exception {
        doThrow(new InventoryReservationNotFoundException("No inventory reservations for checkout: " + CHECKOUT_ID))
                .when(inventoryReservationApplicationService).confirm(CHECKOUT_ID);

        mockMvc.perform(post("/internal/inventory/reservations/{checkoutId}/confirm", CHECKOUT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("No inventory reservations for checkout: " + CHECKOUT_ID));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /internal/inventory/reservations/{checkoutId}/release: 해제 후 204")
    void release_success() throws Exception {
        mockMvc.perform(post("/internal/inventory/reservations/{checkoutId}/release", CHECKOUT_ID))
                .andExpect(status().isNoContent());

        verify(inventoryReservationApplicationService).release(eq(CHECKOUT_ID));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /internal/inventory/reservations/{checkoutId}/release: 예약 없으면 404")
    void release_notFound_returnsNotFound() throws Exception {
        doThrow(new InventoryReservationNotFoundException("No inventory reservations for checkout: " + CHECKOUT_ID))
                .when(inventoryReservationApplicationService).release(CHECKOUT_ID);

        mockMvc.perform(post("/internal/inventory/reservations/{checkoutId}/release", CHECKOUT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("Not Found"));
    }
}
