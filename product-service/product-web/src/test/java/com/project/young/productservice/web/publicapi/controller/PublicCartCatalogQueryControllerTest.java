package com.project.young.productservice.web.publicapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.port.output.view.ReadCartCatalogLineView;
import com.project.young.productservice.application.service.PublicCartCatalogQueryService;
import com.project.young.productservice.web.publicapi.dto.PublicCartCatalogLinesSearchRequest;
import com.project.young.productservice.web.publicapi.mapper.PublicCartCatalogQueryResponseMapper;
import com.project.young.common.application.web.GlobalExceptionHandler;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.controller.TestConfig;
import com.project.young.productservice.web.exception.handler.ProductServiceGlobalExceptionHandler;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicCartCatalogQueryController.class)
@Import({
        SecurityConfig.class,
        TestConfig.class,
        GlobalExceptionHandler.class,
        ProductServiceGlobalExceptionHandler.class,
        PublicCartCatalogQueryResponseMapper.class
})
class PublicCartCatalogQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PublicCartCatalogQueryService publicCartCatalogQueryService;

    @Test
    @DisplayName("POST /public/catalog/cart-lines/search: variant id 목록으로 카탈로그 라인을 조회한다")
    void searchCartLines_success() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.fromString("018f0000-0000-7000-8000-000000000201");

        when(publicCartCatalogQueryService.resolveCartLines(List.of(variantId)))
                .thenReturn(List.of(ReadCartCatalogLineView.builder()
                        .productId(productId)
                        .productVariantId(variantId)
                        .productName("Phone")
                        .brand("Brand")
                        .sku("SKU-1")
                        .imageUrl("https://img")
                        .unitPrice(new BigDecimal("100.00"))
                        .purchasable(true)
                        .stockQuantity(3)
                        .variantOptions(List.of())
                        .build()));

        PublicCartCatalogLinesSearchRequest request = PublicCartCatalogLinesSearchRequest.builder()
                .productVariantIds(List.of(variantId))
                .build();

        mockMvc.perform(post("/public/catalog/cart-lines/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].productVariantId").value(variantId.toString()))
                .andExpect(jsonPath("$.lines[0].productName").value("Phone"))
                .andExpect(jsonPath("$.lines[0].purchasable").value(true))
                .andExpect(jsonPath("$.lines[0].stockQuantity").value(3));
    }
}
