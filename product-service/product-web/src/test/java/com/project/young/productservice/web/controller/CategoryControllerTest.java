
package com.project.young.productservice.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.dto.*;
import com.project.young.productservice.application.service.CategoryApplicationService;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.exception.DuplicateCategoryNameException;
import com.project.young.productservice.web.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CategoryController.class)
@Import({SecurityConfig.class, TestConfig.class})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoryApplicationService categoryApplicationService;

    @Nested
    @DisplayName("Category Creation Tests")
    class CreateCategoryTests {

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should create category and return 201 created when user is ADMIN")
        void createCategory_WithAdminRole_ShouldReturnCreated() throws Exception {
            // Arrange
            CreateCategoryCommand command = new CreateCategoryCommand("Electronics", null);
            CreateCategoryResponse response = new CreateCategoryResponse("Electronics", "Category Electronics created successfully.");
            when(categoryApplicationService.createCategory(any(CreateCategoryCommand.class))).thenReturn(response);

            // Act
            ResultActions resultActions = mockMvc.perform(post("/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(command)));

            // Assert
            resultActions.andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Electronics"))
                    .andExpect(jsonPath("$.message").value("Category Electronics created successfully."));
        }

        @Test
        @WithMockUser(authorities = "CUSTOMER")
        @DisplayName("Should return 403 Forbidden when user is not ADMIN")
        void createCategory_WithNonAdminRole_ShouldReturnForbidden() throws Exception {
            // Arrange
            CreateCategoryCommand command = new CreateCategoryCommand("Electronics", null);

            // Act & Assert
            mockMvc.perform(post("/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should return 400 Bad Request for invalid input")
        void createCategory_WithInvalidInput_ShouldReturnBadRequest() throws Exception {
            // Arrange
            CreateCategoryCommand invalidCommand = new CreateCategoryCommand("", null); // Invalid name

            // Act & Assert
            mockMvc.perform(post("/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidCommand)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should return 409 Conflict when category name already exists")
        void createCategory_DuplicateName_ShouldReturnConflict() throws Exception {
            // Arrange
            CreateCategoryCommand command = new CreateCategoryCommand("Duplicate Category", null);
            when(categoryApplicationService.createCategory(any(CreateCategoryCommand.class)))
                    .thenThrow(new DuplicateCategoryNameException("Category name 'Duplicate Category' already exists."));

            // Act & Assert
            mockMvc.perform(post("/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("Category Update Tests")
    class UpdateCategoryTests {

        private final Long categoryId = 1L;

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should update category name and return 200 OK")
        void updateCategory_NameChange_ShouldReturnOk() throws Exception {
            // Arrange
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("Updated Electronics")
                    .build();

            UpdateCategoryResponse response = new UpdateCategoryResponse(
                    "Updated Electronics",
                    "Category 'Updated Electronics' updated successfully."
            );

            when(categoryApplicationService.updateCategory(eq(categoryId), any(UpdateCategoryCommand.class)))
                    .thenReturn(response);

            // Act
            ResultActions resultActions = mockMvc.perform(put("/categories/{id}", categoryId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(command)));

            // Assert
            resultActions.andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Updated Electronics"))
                    .andExpect(jsonPath("$.message").value("Category 'Updated Electronics' updated successfully."));
        }

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should update category parent and return 200 OK")
        void updateCategory_ParentChange_ShouldReturnOk() throws Exception {
            // Arrange
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("Electronics")
                    .parentId(2L)
                    .build();

            UpdateCategoryResponse response = new UpdateCategoryResponse(
                    "Electronics",
                    "Category 'Electronics' updated successfully."
            );

            when(categoryApplicationService.updateCategory(eq(categoryId), any(UpdateCategoryCommand.class)))
                    .thenReturn(response);

            // Act & Assert
            mockMvc.perform(put("/categories/{id}", categoryId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Electronics"))
                    .andExpect(jsonPath("$.message").value("Category 'Electronics' updated successfully."));
        }

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should update category status and return 200 OK")
        void updateCategory_StatusChange_ShouldReturnOk() throws Exception {
            // Arrange
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("Electronics")
                    .status("INACTIVE")
                    .build();

            UpdateCategoryResponse response = new UpdateCategoryResponse(
                    "Electronics",
                    "Category 'Electronics' updated successfully."
            );

            when(categoryApplicationService.updateCategory(eq(categoryId), any(UpdateCategoryCommand.class)))
                    .thenReturn(response);

            // Act & Assert
            mockMvc.perform(put("/categories/{id}", categoryId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Electronics"))
                    .andExpect(jsonPath("$.message").value("Category 'Electronics' updated successfully."));
        }

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should update all fields and return 200 OK")
        void updateCategory_AllFieldsChange_ShouldReturnOk() throws Exception {
            // Arrange
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("Updated Electronics")
                    .parentId(2L)
                    .status("INACTIVE")
                    .build();

            UpdateCategoryResponse response = new UpdateCategoryResponse(
                    "Updated Electronics",
                    "Category 'Updated Electronics' updated successfully."
            );

            when(categoryApplicationService.updateCategory(eq(categoryId), any(UpdateCategoryCommand.class)))
                    .thenReturn(response);

            // Act & Assert
            mockMvc.perform(put("/categories/{id}", categoryId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Updated Electronics"))
                    .andExpect(jsonPath("$.message").value("Category 'Updated Electronics' updated successfully."));
        }

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should return 404 Not Found when category does not exist")
        void updateCategory_NotFound_ShouldReturnNotFound() throws Exception {
            // Arrange
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("Non-existent")
                    .build();

            when(categoryApplicationService.updateCategory(eq(999L), any(UpdateCategoryCommand.class)))
                    .thenThrow(new CategoryNotFoundException("Category with id 999 not found."));

            // Act & Assert
            mockMvc.perform(put("/categories/{id}", 999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should return 409 Conflict when updating to duplicate name")
        void updateCategory_DuplicateName_ShouldReturnConflict() throws Exception {
            // Arrange
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("Duplicate Name")
                    .build();

            when(categoryApplicationService.updateCategory(eq(categoryId), any(UpdateCategoryCommand.class)))
                    .thenThrow(new DuplicateCategoryNameException("Category name 'Duplicate Name' already exists."));

            // Act & Assert
            mockMvc.perform(put("/categories/{id}", categoryId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isConflict());
        }

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should return 400 Bad Request for invalid parent change")
        void updateCategory_InvalidParentChange_ShouldReturnBadRequest() throws Exception {
            // Arrange
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("Electronics")
                    .parentId(categoryId) // Trying to set itself as parent
                    .build();

            when(categoryApplicationService.updateCategory(eq(categoryId), any(UpdateCategoryCommand.class)))
                    .thenThrow(new CategoryDomainException("Cannot set category as its own parent."));

            // Act & Assert
            mockMvc.perform(put("/categories/{id}", categoryId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should return 400 Bad Request for invalid status")
        void updateCategory_InvalidStatus_ShouldReturnBadRequest() throws Exception {
            // Arrange
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("Electronics")
                    .status("INVALID_STATUS")
                    .build();

            // Act & Assert - Validation should fail before reaching the service
            mockMvc.perform(put("/categories/{id}", categoryId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should return 400 Bad Request for blank name")
        void updateCategory_BlankName_ShouldReturnBadRequest() throws Exception {
            // Arrange
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("") // Blank name
                    .build();

            // Act & Assert - Validation should fail before reaching the service
            mockMvc.perform(put("/categories/{id}", categoryId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should return 400 Bad Request for negative parent ID")
        void updateCategory_NegativeParentId_ShouldReturnBadRequest() throws Exception {
            // Arrange
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("Electronics")
                    .parentId(-1L) // Negative parent ID
                    .build();

            // Act & Assert - Validation should fail before reaching the service
            mockMvc.perform(put("/categories/{id}", categoryId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(authorities = "CUSTOMER")
        @DisplayName("Should return 403 Forbidden when user is not ADMIN")
        void updateCategory_WithNonAdminRole_ShouldReturnForbidden() throws Exception {
            // Arrange
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("Electronics")
                    .build();

            // Act & Assert
            mockMvc.perform(put("/categories/{id}", categoryId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Category Deletion Tests")
    class DeleteCategoryTests {

        private final Long categoryId = 1L;

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should delete category and return 200 OK")
        void deleteCategory_Success_ShouldReturnOk() throws Exception {
            // Arrange
            DeleteCategoryResponse response = new DeleteCategoryResponse(
                    categoryId,
                    "Category Electronics (ID: 1) marked as deleted successfully."
            );
            when(categoryApplicationService.deleteCategory(eq(categoryId))).thenReturn(response);

            // Act & Assert
            mockMvc.perform(delete("/categories/{id}", categoryId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(categoryId))
                    .andExpect(jsonPath("$.message").value("Category Electronics (ID: 1) marked as deleted successfully."));
        }

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should return 404 Not Found when trying to delete a non-existent category")
        void deleteCategory_NotFound_ShouldReturnNotFound() throws Exception {
            // Arrange
            when(categoryApplicationService.deleteCategory(eq(999L)))
                    .thenThrow(new CategoryNotFoundException("Category with id 999 not found, cannot delete."));

            // Act & Assert
            mockMvc.perform(delete("/categories/{id}", 999L))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should return 400 Bad Request when trying to delete already deleted category")
        void deleteCategory_AlreadyDeleted_ShouldReturnBadRequest() throws Exception {
            // Arrange
            when(categoryApplicationService.deleteCategory(eq(categoryId)))
                    .thenThrow(new CategoryDomainException("Cannot delete a category that has been already deleted."));

            // Act & Assert
            mockMvc.perform(delete("/categories/{id}", categoryId))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(authorities = "CUSTOMER")
        @DisplayName("Should return 403 Forbidden when user is not ADMIN")
        void deleteCategory_WithNonAdminRole_ShouldReturnForbidden() throws Exception {
            // Act & Assert
            mockMvc.perform(delete("/categories/{id}", categoryId))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }
    }
}