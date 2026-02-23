package com.project.young.productservice.web.controller;

import com.project.young.productservice.application.port.output.view.ReadCategoryView;
import com.project.young.productservice.application.service.CategoryQueryService;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.dto.ReadCategoryNodeResponse;
import com.project.young.productservice.web.dto.ReadCategoryResponse;
import com.project.young.productservice.web.mapper.CategoryQueryResponseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryQueryController.class)
@Import({SecurityConfig.class, TestConfig.class})
class CategoryQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryQueryService categoryQueryService;

    @MockitoBean
    private CategoryQueryResponseMapper categoryQueryResponseMapper;

    @Nested
    @DisplayName("GET /queries/categories/hierarchy")
    class PublicHierarchyTests {

        @Test
        @DisplayName("전체 활성 카테고리 계층 조회 성공 시 200 OK와 ReadCategoryResponse 반환")
        void getAllActiveCategoryHierarchy_Success() throws Exception {
            // Given
            ReadCategoryView view = ReadCategoryView.builder()
                    .id(1L)
                    .name("전자제품")
                    .parentId(null)
                    .status(CategoryStatus.ACTIVE)
                    .children(List.of())
                    .build();

            ReadCategoryNodeResponse node = ReadCategoryNodeResponse.builder()
                    .id(1L)
                    .name("전자제품")
                    .parentId(null)
                    .status("ACTIVE")
                    .children(List.of())
                    .build();

            ReadCategoryResponse response = ReadCategoryResponse.builder()
                    .categories(List.of(node))
                    .build();

            when(categoryQueryService.getAllActiveCategoryHierarchy())
                    .thenReturn(List.of(view));
            when(categoryQueryResponseMapper.toReadCategoryResponse(anyList()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(get("/queries/categories/hierarchy"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categories[0].id").value(1))
                    .andExpect(jsonPath("$.categories[0].name").value("전자제품"))
                    .andExpect(jsonPath("$.categories[0].status").value("ACTIVE"));

            verify(categoryQueryService).getAllActiveCategoryHierarchy();
            verify(categoryQueryResponseMapper).toReadCategoryResponse(anyList());
        }
    }

    @Nested
    @DisplayName("GET /queries/categories/admin/hierarchy")
    class AdminHierarchyTests {

        @Test
        @DisplayName("ADMIN 권한으로 관리자용 계층 조회 성공 시 200 OK와 ReadCategoryResponse 반환")
        @WithMockUser(authorities = "ADMIN")
        void getAdminCategoryHierarchy_WithAdmin_Returns200AndResponse() throws Exception {
            // Given
            ReadCategoryView view = ReadCategoryView.builder()
                    .id(1L)
                    .name("전자제품")
                    .parentId(null)
                    .status(CategoryStatus.ACTIVE)
                    .children(List.of())
                    .build();

            ReadCategoryNodeResponse node = ReadCategoryNodeResponse.builder()
                    .id(1L)
                    .name("전자제품")
                    .parentId(null)
                    .status("ACTIVE")
                    .children(List.of())
                    .build();

            ReadCategoryResponse response = ReadCategoryResponse.builder()
                    .categories(List.of(node))
                    .build();

            when(categoryQueryService.getAdminCategoryHierarchy())
                    .thenReturn(List.of(view));
            when(categoryQueryResponseMapper.toReadCategoryResponse(anyList()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(get("/queries/categories/admin/hierarchy"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categories[0].id").value(1))
                    .andExpect(jsonPath("$.categories[0].name").value("전자제품"))
                    .andExpect(jsonPath("$.categories[0].status").value("ACTIVE"));

            verify(categoryQueryService).getAdminCategoryHierarchy();
            verify(categoryQueryResponseMapper).toReadCategoryResponse(anyList());
        }

        @Test
        @DisplayName("ADMIN 권한 없으면 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void getAdminCategoryHierarchy_WithoutAdmin_Returns403() throws Exception {
            mockMvc.perform(get("/queries/categories/admin/hierarchy"))
                    .andExpect(status().isForbidden());

            verify(categoryQueryService, never()).getAdminCategoryHierarchy();
            verify(categoryQueryResponseMapper, never()).toReadCategoryResponse(anyList());
        }
    }
}