package com.project.young.productservice.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.dto.CreateCategoryCommand;
import com.project.young.productservice.application.dto.CreateCategoryResult;
import com.project.young.productservice.application.dto.DeleteCategoryResult;
import com.project.young.productservice.application.dto.UpdateCategoryCommand;
import com.project.young.productservice.application.dto.UpdateCategoryResult;
import com.project.young.productservice.application.service.CategoryApplicationService;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.dto.CreateCategoryResponse;
import com.project.young.productservice.web.dto.DeleteCategoryResponse;
import com.project.young.productservice.web.dto.UpdateCategoryResponse;
import com.project.young.productservice.web.mapper.CategoryResponseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, TestConfig.class})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryApplicationService categoryApplicationService;

    @MockitoBean
    private CategoryResponseMapper categoryResponseMapper;

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("유효한 요청 시 201 CREATED와 CreateCategoryResponse 반환")
        @WithMockUser(authorities = "ADMIN")
        void create_ValidRequest_Returns201AndResponse() throws Exception {
            // Given
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();

            CreateCategoryResult serviceResult = new CreateCategoryResult(1L, "전자제품");
            CreateCategoryResponse expectedResponse = CreateCategoryResponse.builder()
                    .id(1L)
                    .name("전자제품")
                    .message("Category created successfully.")
                    .build();

            when(categoryApplicationService.createCategory(any(CreateCategoryCommand.class)))
                    .thenReturn(serviceResult);
            when(categoryResponseMapper.toCreateCategoryResponse(serviceResult))
                    .thenReturn(expectedResponse);

            // When & Then
            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("전자제품"))
                    .andExpect(jsonPath("$.message").exists());

            verify(categoryApplicationService).createCategory(any(CreateCategoryCommand.class));
            verify(categoryResponseMapper).toCreateCategoryResponse(serviceResult);
        }

        @Test
        @DisplayName("ADMIN 권한 없으면 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void create_WithoutAdminAuthority_Returns403() throws Exception {
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isForbidden());

            verify(categoryApplicationService, never()).createCategory(any());
        }

        @Test
        @DisplayName("name이 blank면 400 Bad Request")
        @WithMockUser(authorities = "ADMIN")
        void create_BlankName_Returns400() throws Exception {
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("  ")
                    .parentId(null)
                    .build();

            mockMvc.perform(post("/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isBadRequest());

            verify(categoryApplicationService, never()).createCategory(any());
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("유효한 요청 시 200 OK와 UpdateCategoryResponse 반환")
        @WithMockUser(authorities = "ADMIN")
        void update_ValidRequest_Returns200AndResponse() throws Exception {
            // Given
            Long categoryId = 1L;
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("전자제품 수정")
                    .parentId(null)
                    .status(CategoryStatus.ACTIVE)
                    .build();

            UpdateCategoryResult serviceResult = new UpdateCategoryResult(
                    1L, "전자제품 수정", null, CategoryStatus.ACTIVE);
            UpdateCategoryResponse expectedResponse = UpdateCategoryResponse.builder()
                    .id(1L)
                    .name("전자제품 수정")
                    .parentId(null)
                    .status("ACTIVE")
                    .message("Category updated successfully.")
                    .build();

            when(categoryApplicationService.updateCategory(eq(categoryId), any(UpdateCategoryCommand.class)))
                    .thenReturn(serviceResult);
            when(categoryResponseMapper.toUpdateCategoryResponse(serviceResult))
                    .thenReturn(expectedResponse);

            // When & Then
            mockMvc.perform(put("/categories/{categoryId}", categoryId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("전자제품 수정"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.message").exists());

            verify(categoryApplicationService).updateCategory(eq(categoryId), any(UpdateCategoryCommand.class));
            verify(categoryResponseMapper).toUpdateCategoryResponse(serviceResult);
        }

        @Test
        @DisplayName("ADMIN 권한 없으면 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void update_WithoutAdminAuthority_Returns403() throws Exception {
            Long categoryId = 1L;
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("전자제품")
                    .parentId(null)
                    .status(CategoryStatus.ACTIVE)
                    .build();

            mockMvc.perform(put("/categories/{categoryId}", categoryId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andExpect(status().isForbidden());

            verify(categoryApplicationService, never()).updateCategory(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("유효한 categoryId로 삭제 시 200 OK와 DeleteCategoryResponse 반환")
        @WithMockUser(authorities = "ADMIN")
        void delete_ValidCategoryId_Returns200AndResponse() throws Exception {
            // Given
            Long categoryId = 1L;
            DeleteCategoryResult serviceResult = new DeleteCategoryResult(1L, "전자제품");
            DeleteCategoryResponse expectedResponse = DeleteCategoryResponse.builder()
                    .id(1L)
                    .name("전자제품")
                    .message("Category deleted successfully.")
                    .build();

            when(categoryApplicationService.deleteCategory(categoryId)).thenReturn(serviceResult);
            when(categoryResponseMapper.toDeleteCategoryResponse(serviceResult))
                    .thenReturn(expectedResponse);

            // When & Then
            mockMvc.perform(delete("/categories/{categoryId}", categoryId)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("전자제품"))
                    .andExpect(jsonPath("$.message").exists());

            verify(categoryApplicationService).deleteCategory(categoryId);
            verify(categoryResponseMapper).toDeleteCategoryResponse(serviceResult);
        }

        @Test
        @DisplayName("ADMIN 권한 없으면 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void delete_WithoutAdminAuthority_Returns403() throws Exception {
            Long categoryId = 1L;

            mockMvc.perform(delete("/categories/{categoryId}", categoryId)
                            .with(csrf()))
                    .andExpect(status().isForbidden());

            verify(categoryApplicationService, never()).deleteCategory(anyLong());
        }
    }
}