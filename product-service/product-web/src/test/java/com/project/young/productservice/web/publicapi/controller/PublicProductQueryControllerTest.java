package com.project.young.productservice.web.publicapi.controller;

import com.project.young.productservice.application.dto.query.PublicProductListQuery;
import com.project.young.productservice.application.dto.result.PublicProductListPageResult;
import com.project.young.productservice.application.service.PublicProductQueryService;
import com.project.young.common.application.web.GlobalExceptionHandler;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.controller.TestConfig;
import com.project.young.productservice.web.exception.handler.ProductServiceGlobalExceptionHandler;
import com.project.young.productservice.web.publicapi.dto.PublicProductPageResponse;
import com.project.young.productservice.web.publicapi.mapper.PublicProductQueryResponseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicProductQueryController.class)
@Import({SecurityConfig.class, TestConfig.class, GlobalExceptionHandler.class, ProductServiceGlobalExceptionHandler.class})
class PublicProductQueryControllerTest {

  private static final long CATEGORY_ID = 12L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PublicProductQueryService publicProductQueryService;

  @MockitoBean private PublicProductQueryResponseMapper publicProductQueryResponseMapper;

  @Test
  @DisplayName("GET /public/products — categoryId 필수, 인증 없이 200")
  void listProducts_withCategoryId_returnsPage() throws Exception {
    PublicProductListPageResult serviceResult =
        new PublicProductListPageResult(List.of(), 0, 24, 0L, 0);
    PublicProductPageResponse response =
        PublicProductPageResponse.builder()
            .content(List.of())
            .page(0)
            .size(24)
            .totalElements(0L)
            .totalPages(0)
            .build();

    when(publicProductQueryService.listProductsByCategory(any(PublicProductListQuery.class)))
        .thenReturn(serviceResult);
    when(publicProductQueryResponseMapper.toPublicProductPageResponse(serviceResult))
        .thenReturn(response);

    mockMvc
        .perform(get("/public/products").param("categoryId", String.valueOf(CATEGORY_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(24));

    verify(publicProductQueryService).listProductsByCategory(any(PublicProductListQuery.class));
  }

  @Test
  @DisplayName("GET /public/products — categoryId 누락 시 400")
  void listProducts_withoutCategoryId_returnsBadRequest() throws Exception {
    mockMvc.perform(get("/public/products")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /public/products — q, sort, brand, price 파라미터 전달")
  void listProducts_withFilters_invokesService() throws Exception {
    PublicProductListPageResult serviceResult =
        new PublicProductListPageResult(List.of(), 1, 10, 0L, 0);
    when(publicProductQueryService.listProductsByCategory(any(PublicProductListQuery.class)))
        .thenReturn(serviceResult);
    when(publicProductQueryResponseMapper.toPublicProductPageResponse(serviceResult))
        .thenReturn(
            PublicProductPageResponse.builder()
                .content(List.of())
                .page(1)
                .size(10)
                .totalElements(0L)
                .totalPages(0)
                .build());

    mockMvc
        .perform(
            get("/public/products")
                .param("categoryId", String.valueOf(CATEGORY_ID))
                .param("page", "1")
                .param("size", "10")
                .param("q", "denim")
                .param("sort", "price_desc")
                .param("brand", "BrandA")
                .param("minPrice", "1000")
                .param("maxPrice", "90000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(10));
  }
}
