package com.project.young.productservice.web.publicapi.controller;

import com.project.young.common.application.web.GlobalExceptionHandler;
import com.project.young.productservice.application.dto.query.PublicProductFacetQuery;
import com.project.young.productservice.application.dto.query.PublicProductFacetType;
import com.project.young.productservice.application.dto.result.PublicProductFacetResult;
import com.project.young.productservice.application.service.PublicProductFacetQueryService;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.controller.TestConfig;
import com.project.young.productservice.web.exception.handler.ProductServiceGlobalExceptionHandler;
import com.project.young.productservice.web.publicapi.dto.PublicProductFacetRequest;
import com.project.young.productservice.web.publicapi.dto.PublicProductFacetResponse;
import com.project.young.productservice.web.publicapi.dto.PublicProductFacetGroupResponse;
import com.project.young.productservice.web.publicapi.mapper.PublicProductFacetRequestMapper;
import com.project.young.productservice.web.publicapi.mapper.PublicProductFacetResponseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicProductFacetQueryController.class)
@Import({SecurityConfig.class, TestConfig.class, GlobalExceptionHandler.class, ProductServiceGlobalExceptionHandler.class})
class PublicProductFacetQueryControllerTest {

    private static final long CATEGORY_ID = 12L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicProductFacetQueryService publicProductFacetQueryService;

    @MockitoBean
    private PublicProductFacetRequestMapper publicProductFacetRequestMapper;

    @MockitoBean
    private PublicProductFacetResponseMapper publicProductFacetResponseMapper;

    @Test
    @DisplayName("GET /public/products/facets returns 400 when categoryId missing")
    void getFacets_withoutCategoryId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/public/products/facets"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /public/products/facets returns 400 when facet is invalid")
    void getFacets_withInvalidFacet_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/public/products/facets")
                        .param("categoryId", String.valueOf(CATEGORY_ID))
                        .param("facet", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /public/products/facets returns 200 with categoryId")
    void getFacets_withCategoryId_returnsResponse() throws Exception {
        PublicProductFacetQuery query = new PublicProductFacetQuery(CATEGORY_ID, null, List.of(), null, null, List.of());
        PublicProductFacetResult serviceResult = new PublicProductFacetResult(CATEGORY_ID, 0L, List.of(), List.of());
        PublicProductFacetResponse response = PublicProductFacetResponse.builder()
                .categoryId(CATEGORY_ID)
                .totalMatching(0L)
                .facets(List.of())
                .build();

        when(publicProductFacetRequestMapper.toQuery(any(PublicProductFacetRequest.class))).thenReturn(query);
        when(publicProductFacetQueryService.getFacets(query)).thenReturn(serviceResult);
        when(publicProductFacetResponseMapper.toResponse(serviceResult)).thenReturn(response);

        mockMvc.perform(get("/public/products/facets").param("categoryId", String.valueOf(CATEGORY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(CATEGORY_ID))
                .andExpect(jsonPath("$.totalMatching").value(0))
                .andExpect(jsonPath("$.facets").isArray());

        verify(publicProductFacetRequestMapper).toQuery(any(PublicProductFacetRequest.class));
        verify(publicProductFacetQueryService).getFacets(query);
    }

    @Test
    @DisplayName("GET /public/products/facets binds q, brands, prices, facet params into request DTO")
    void getFacets_withFilterParams_passesMappedRequestToService() throws Exception {
        PublicProductFacetQuery query = new PublicProductFacetQuery(
                CATEGORY_ID,
                "denim",
                List.of("Nike", "Puma"),
                new BigDecimal("10"),
                new BigDecimal("999"),
                List.of(PublicProductFacetType.BRAND, PublicProductFacetType.PRICE)
        );
        PublicProductFacetResult serviceResult = new PublicProductFacetResult(CATEGORY_ID, 2L, List.of(), List.of());
        PublicProductFacetResponse response = PublicProductFacetResponse.builder()
                .categoryId(CATEGORY_ID)
                .totalMatching(2L)
                .facets(List.of(
                        PublicProductFacetGroupResponse.builder()
                                .key("brand")
                                .type("terms")
                                .terms(List.of())
                                .ranges(List.of())
                                .build()
                ))
                .build();

        when(publicProductFacetRequestMapper.toQuery(any(PublicProductFacetRequest.class))).thenReturn(query);
        when(publicProductFacetQueryService.getFacets(query)).thenReturn(serviceResult);
        when(publicProductFacetResponseMapper.toResponse(serviceResult)).thenReturn(response);

        mockMvc.perform(get("/public/products/facets")
                        .param("categoryId", String.valueOf(CATEGORY_ID))
                        .param("q", "denim")
                        .param("brands", "Nike", "Puma")
                        .param("minPrice", "10")
                        .param("maxPrice", "999")
                        .param("facet", "brand")
                        .param("facet", "price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMatching").value(2));

        ArgumentCaptor<PublicProductFacetRequest> requestCaptor =
                ArgumentCaptor.forClass(PublicProductFacetRequest.class);
        verify(publicProductFacetRequestMapper).toQuery(requestCaptor.capture());
        PublicProductFacetRequest captured = requestCaptor.getValue();
        assertThat(captured.categoryId()).isEqualTo(CATEGORY_ID);
        assertThat(captured.q()).isEqualTo("denim");
        assertThat(captured.brands()).containsExactly("Nike", "Puma");
        assertThat(captured.minPrice()).isEqualByComparingTo("10");
        assertThat(captured.maxPrice()).isEqualByComparingTo("999");
        assertThat(captured.facets()).containsExactlyInAnyOrder(PublicProductFacetType.BRAND, PublicProductFacetType.PRICE);

        verify(publicProductFacetQueryService).getFacets(query);
    }
}
