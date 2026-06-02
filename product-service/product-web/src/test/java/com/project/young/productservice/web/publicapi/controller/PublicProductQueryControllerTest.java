package com.project.young.productservice.web.publicapi.controller;

import com.project.young.productservice.application.dto.query.PublicProductListQuery;
import com.project.young.productservice.application.dto.result.PublicProductListPageResult;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.application.service.PublicProductQueryService;
import com.project.young.common.application.web.GlobalExceptionHandler;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.controller.TestConfig;
import com.project.young.productservice.web.exception.handler.ProductServiceGlobalExceptionHandler;
import com.project.young.productservice.web.publicapi.dto.PublicProductDetailResponse;
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
import java.util.UUID;

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
                .param("brands", "BrandA", "BrandB")
                .param("minPrice", "1000")
                .param("maxPrice", "90000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(10));
  }

  @Test
  @DisplayName("GET /public/products/{productId} — 상세 조회")
  void getProductDetail_returnsOk() throws Exception {
    UUID productId = UUID.randomUUID();
    ReadProductDetailView serviceResult =
        ReadProductDetailView.builder()
            .id(productId)
            .categoryId(CATEGORY_ID)
            .name("Preview Product")
            .description("desc")
            .brand("Brand")
            .mainImageUrl("https://example.com/a.jpg")
            .basePrice(java.math.BigDecimal.valueOf(10000))
            .status(com.project.young.productservice.domain.valueobject.ProductStatus.INACTIVE)
            .conditionType(com.project.young.productservice.domain.valueobject.ConditionType.NEW)
            .images(List.of())
            .optionGroups(List.of())
            .variants(List.of())
            .build();
    PublicProductDetailResponse response =
        PublicProductDetailResponse.builder()
            .id(productId)
            .categoryId(CATEGORY_ID)
            .name("Preview Product")
            .description("desc")
            .brand("Brand")
            .mainImageUrl("https://example.com/a.jpg")
            .basePrice(java.math.BigDecimal.valueOf(10000))
            .status("INACTIVE")
            .conditionType("NEW")
            .purchasable(false)
            .listedInCatalog(false)
            .images(List.of())
            .optionGroups(List.of())
            .variants(List.of())
            .build();

    when(publicProductQueryService.getStorefrontProductDetail(any())).thenReturn(serviceResult);
    when(publicProductQueryResponseMapper.toPublicProductDetailResponse(serviceResult)).thenReturn(response);

    mockMvc
        .perform(get("/public/products/{productId}", productId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(productId.toString()))
        .andExpect(jsonPath("$.status").value("INACTIVE"))
        .andExpect(jsonPath("$.purchasable").value(false));
  }

  @Test
  @DisplayName("GET /public/products/{productId} — 없는 상품이면 404")
  void getProductDetail_whenNotFound_returns404() throws Exception {
    UUID productId = UUID.randomUUID();
    when(publicProductQueryService.getStorefrontProductDetail(any()))
        .thenThrow(new ProductNotFoundException("Product not found or not visible: " + productId));

    mockMvc
        .perform(get("/public/products/{productId}", productId))
        .andExpect(status().isNotFound());
  }
}
