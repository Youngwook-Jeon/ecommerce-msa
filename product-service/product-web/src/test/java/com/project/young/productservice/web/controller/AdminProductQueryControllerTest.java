package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.dto.result.AdminProductDetailResult;
import com.project.young.productservice.application.dto.condition.AdminProductSearchCondition;
import com.project.young.productservice.application.port.output.AdminProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.application.service.AdminProductQueryService;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.dto.AdminProductDetailResponse;
import com.project.young.productservice.web.dto.AdminProductListItemResponse;
import com.project.young.productservice.web.dto.AdminProductPageResponse;
import com.project.young.productservice.web.mapper.AdminProductQueryResponseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminProductQueryController.class)
@Import({SecurityConfig.class, TestConfig.class})
class AdminProductQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminProductQueryService adminProductQueryService;

    @MockitoBean
    private AdminProductQueryResponseMapper adminProductQueryResponseMapper;

    @Nested
    @DisplayName("GET /admin/queries/products/{productId}")
    class GetDetailTests {
        @Test
        @DisplayName("ADMIN 권한으로 상세 조회 시 200 OK와 AdminProductDetailResponse 반환")
        @WithMockUser(authorities = "ADMIN")
        void getDetail_WithAdmin_Returns200AndResponse() throws Exception {
            // Given
            UUID productId = UUID.randomUUID();
            Instant now = Instant.now();
            AdminProductDetailResult serviceResult = AdminProductDetailResult.builder()
                    .id(productId)
                    .categoryId(1L)
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다.")
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .basePrice(new BigDecimal("99000"))
                    .status(ProductStatus.ACTIVE)
                    .conditionType(ConditionType.NEW)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            AdminProductDetailResponse response = AdminProductDetailResponse.builder()
                    .id(productId)
                    .categoryId(1L)
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다.")
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .basePrice(new BigDecimal("99000"))
                    .status("ACTIVE")
                    .conditionType("NEW")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            when(adminProductQueryService.getProductDetail(any()))
                    .thenReturn(serviceResult);
            when(adminProductQueryResponseMapper.toAdminProductDetailResponse(serviceResult))
                    .thenReturn(response);
            // When & Then
            mockMvc.perform(get("/admin/queries/products/{productId}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId.toString()))
                    .andExpect(jsonPath("$.name").value("와이드핏 데님"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.conditionType").value("NEW"));
            verify(adminProductQueryService).getProductDetail(any());
            verify(adminProductQueryResponseMapper).toAdminProductDetailResponse(serviceResult);
        }

        @Test
        @DisplayName("ADMIN 권한 없으면 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void getDetail_WithoutAdmin_Returns403() throws Exception {
            UUID productId = UUID.randomUUID();
            mockMvc.perform(get("/admin/queries/products/{productId}", productId))
                    .andExpect(status().isForbidden());
            verify(adminProductQueryService, never()).getProductDetail(any());
            verify(adminProductQueryResponseMapper, never()).toAdminProductDetailResponse(any());
        }
    }


    @Nested
    @DisplayName("GET /admin/queries/products")
    class SearchTests {

        @Test
        @DisplayName("ADMIN 권한으로 검색 시 200 OK와 AdminProductPageResponse 반환")
        @WithMockUser(authorities = "ADMIN")
        void search_WithAdmin_Returns200AndResponse() throws Exception {
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

            AdminProductReadRepository.AdminProductSearchResult serviceResult =
                    new AdminProductReadRepository.AdminProductSearchResult(
                            List.of(view),
                            0,
                            20,
                            1,
                            1
                    );

            AdminProductListItemResponse item = AdminProductListItemResponse.builder()
                    .id(productId)
                    .categoryId(1L)
                    .name("와이드핏 데님")
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .basePrice(new BigDecimal("99000"))
                    .status("ACTIVE")
                    .conditionType("NEW")
                    .build();

            AdminProductPageResponse response = AdminProductPageResponse.builder()
                    .content(List.of(item))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            when(adminProductQueryService.search(any(AdminProductSearchCondition.class), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(serviceResult);
            when(adminProductQueryResponseMapper.toAdminProductPageResponse(
                    anyList(), anyInt(), anyInt(), anyLong(), anyInt()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(get("/admin/queries/products")
                            .param("page", "0")
                            .param("size", "20")
                            .param("sort", "createdAt,desc")
                            .param("categoryId", "1")
                            .param("includeOrphans", "true")
                            .param("status", "ACTIVE")
                            .param("brand", "브랜드A")
                            .param("keyword", "데님"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(productId.toString()))
                    .andExpect(jsonPath("$.content[0].name").value("와이드핏 데님"))
                    .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1));

            verify(adminProductQueryService).search(any(AdminProductSearchCondition.class), anyInt(), anyInt(), anyString(), anyBoolean());
            verify(adminProductQueryResponseMapper).toAdminProductPageResponse(
                    anyList(), anyInt(), anyInt(), anyLong(), anyInt());
        }

        @Test
        @DisplayName("ADMIN 권한 없으면 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void search_WithoutAdmin_Returns403() throws Exception {
            mockMvc.perform(get("/admin/queries/products"))
                    .andExpect(status().isForbidden());

            verify(adminProductQueryService, never()).search(any(), anyInt(), anyInt(), anyString(), anyBoolean());
            verify(adminProductQueryResponseMapper, never()).toAdminProductPageResponse(anyList(), anyInt(), anyInt(), anyLong(), anyInt());
        }
    }
}

