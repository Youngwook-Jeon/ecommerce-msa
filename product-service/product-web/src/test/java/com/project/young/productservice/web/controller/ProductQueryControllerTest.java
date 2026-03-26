package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.application.service.ProductQueryService;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.dto.ReadProductDetailResponse;
import com.project.young.productservice.web.dto.ReadProductListResponse;
import com.project.young.productservice.web.mapper.ProductQueryResponseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductQueryController.class)
@Import({SecurityConfig.class, TestConfig.class})
class ProductQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductQueryService productQueryService;

    @MockitoBean
    private ProductQueryResponseMapper productQueryResponseMapper;

    @Nested
    @DisplayName("GET /queries/products")
    class GetAllVisibleProductsTests {

        @Test
        @DisplayName("전체 보이는 상품 목록 조회 시 200 OK와 ReadProductListResponse 반환")
        void getAllVisibleProducts_Success() throws Exception {
            // Given
            UUID productId = UUID.randomUUID();
            ReadProductView view = ReadProductView.builder()
                    .id(productId)
                    .categoryId(1L)
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다.")
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .basePrice(new BigDecimal("99000"))
                    .status(ProductStatus.ACTIVE)
                    .conditionType(ConditionType.NEW)
                    .build();

            ReadProductDetailResponse item = ReadProductDetailResponse.builder()
                    .id(productId)
                    .categoryId(1L)
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다.")
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .basePrice(new BigDecimal("99000"))
                    .status("ACTIVE")
                    .conditionType("NEW")
                    .build();

            ReadProductListResponse response = ReadProductListResponse.builder()
                    .products(List.of(item))
                    .build();

            when(productQueryService.getAllVisibleProducts())
                    .thenReturn(List.of(view));
            when(productQueryResponseMapper.toReadProductListResponse(anyList()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(get("/queries/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.products[0].id").value(productId.toString()))
                    .andExpect(jsonPath("$.products[0].name").value("와이드핏 데님"))
                    .andExpect(jsonPath("$.products[0].status").value("ACTIVE"));

            verify(productQueryService).getAllVisibleProducts();
            verify(productQueryResponseMapper).toReadProductListResponse(anyList());
        }
    }

    @Nested
    @DisplayName("GET /queries/products/categories/{categoryId}")
    class GetVisibleProductsByCategoryTests {

        @Test
        @DisplayName("카테고리별 보이는 상품 목록 조회 시 200 OK와 ReadProductListResponse 반환")
        void getVisibleProductsByCategory_Success() throws Exception {
            // Given
            Long categoryId = 1L;
            UUID productId = UUID.randomUUID();

            ReadProductView view = ReadProductView.builder()
                    .id(productId)
                    .categoryId(categoryId)
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다.")
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .basePrice(new BigDecimal("99000"))
                    .status(ProductStatus.ACTIVE)
                    .conditionType(ConditionType.NEW)
                    .build();

            ReadProductDetailResponse item = ReadProductDetailResponse.builder()
                    .id(productId)
                    .categoryId(categoryId)
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다.")
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .basePrice(new BigDecimal("99000"))
                    .status("ACTIVE")
                    .conditionType("NEW")
                    .build();

            ReadProductListResponse response = ReadProductListResponse.builder()
                    .products(List.of(item))
                    .build();

            when(productQueryService.getVisibleProductsByCategory(any()))
                    .thenReturn(List.of(view));
            when(productQueryResponseMapper.toReadProductListResponse(anyList()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(get("/queries/products/categories/{categoryId}", categoryId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.products[0].categoryId").value(categoryId))
                    .andExpect(jsonPath("$.products[0].name").value("와이드핏 데님"));

            verify(productQueryService).getVisibleProductsByCategory(any());
            verify(productQueryResponseMapper).toReadProductListResponse(anyList());
        }
    }

    @Nested
    @DisplayName("GET /queries/products/{productId}")
    class GetVisibleProductDetailTests {

        @Test
        @DisplayName("단일 상품 상세 조회 시 200 OK와 ReadProductDetailResponse 반환")
        void getVisibleProductDetail_Success() throws Exception {
            // Given
            UUID productId = UUID.randomUUID();

            ReadProductDetailView view = ReadProductDetailView.builder()
                    .id(productId)
                    .categoryId(1L)
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다.")
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .basePrice(new BigDecimal("99000"))
                    .status(ProductStatus.ACTIVE)
                    .conditionType(ConditionType.NEW)
                    .build();

            ReadProductDetailResponse response = ReadProductDetailResponse.builder()
                    .id(productId)
                    .categoryId(1L)
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다.")
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .basePrice(new BigDecimal("99000"))
                    .status("ACTIVE")
                    .conditionType("NEW")
                    .build();

            when(productQueryService.getVisibleProductDetail(any()))
                    .thenReturn(view);
            when(productQueryResponseMapper.toReadProductDetailResponse(any(ReadProductDetailView.class)))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(get("/queries/products/{productId}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.name").value("와이드핏 데님"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));

            verify(productQueryService).getVisibleProductDetail(any());
            verify(productQueryResponseMapper).toReadProductDetailResponse(any(ReadProductDetailView.class));
        }
    }
}

