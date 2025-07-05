package com.project.young.productservice.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.dto.*;
import com.project.young.productservice.application.service.CategoryApplicationService;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
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
@Import({SecurityConfig.class, TestConfig.class}) // Import security config to test @PreAuthorize
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean // Create a mock of CategoryApplicationService
    private CategoryApplicationService categoryApplicationService;

    @Nested
    @DisplayName("Category Creation Tests")
    class CreateCategoryTests {

        @Test
        @WithMockUser(authorities = "ADMIN") // Simulate a user with ADMIN authority
        @DisplayName("Should create category and return 201 created when user is ADMIN")
        void createCategory_WithAdminRole_ShouldReturnOk() throws Exception {
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
        @WithMockUser(authorities = "CUSTOMER") // Simulate a user with non-ADMIN authority
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
            // No need to mock the service call, as validation should fail before that
            mockMvc.perform(post("/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidCommand)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Category Update Tests")
    class UpdateCategoryTests {

        private final Long categoryId = 1L;

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should update category and return 200 OK")
        void updateCategory_Success_ShouldReturnOk() throws Exception {
            // Arrange
            UpdateCategoryCommand command = new UpdateCategoryCommand("New Electronics", null);

            // Correctly create the response DTO with 'name' and 'message' fields
            UpdateCategoryResponse response = new UpdateCategoryResponse(
                    "New Electronics",
                    "Category New Electronics updated successfully."
            );

            when(categoryApplicationService.updateCategory(eq(categoryId), any(UpdateCategoryCommand.class))).thenReturn(response);

            // Act
            ResultActions resultActions = mockMvc.perform(put("/categories/{id}", categoryId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(command)));

            // Assert
            resultActions.andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("New Electronics"))
                    .andExpect(jsonPath("$.message").value("Category New Electronics updated successfully."));
        }

        @Test
        @WithMockUser(authorities = "ADMIN")
        @DisplayName("Should return 404 Not Found when category does not exist")
        void updateCategory_NotFound_ShouldReturnNotFound() throws Exception {
            // Arrange
            UpdateCategoryCommand command = new UpdateCategoryCommand("Non-existent", null);
            when(categoryApplicationService.updateCategory(eq(999L), any(UpdateCategoryCommand.class)))
                    .thenThrow(new CategoryNotFoundException("Category with id 999 not found."));

            // Act & Assert
            mockMvc.perform(put("/categories/{id}", 999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(command)))
                    .andDo(print())
                    .andExpect(status().isNotFound()); // Assuming GlobalExceptionHandler maps this to 404
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
            DeleteCategoryResponse response = new DeleteCategoryResponse(categoryId, "Category marked as deleted successfully.");
            when(categoryApplicationService.deleteCategory(eq(categoryId))).thenReturn(response);

            // Act & Assert
            mockMvc.perform(delete("/categories/{id}", categoryId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(categoryId))
                    .andExpect(jsonPath("$.message").value("Category marked as deleted successfully."));
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
    }
}
