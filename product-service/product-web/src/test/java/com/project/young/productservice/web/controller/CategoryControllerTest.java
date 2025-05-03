package com.project.young.productservice.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.application.dto.CreateCategoryCommand;
import com.project.young.productservice.application.dto.CreateCategoryResponse;
import com.project.young.productservice.application.service.CategoryApplicationService;
import com.project.young.productservice.web.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, TestConfig.class})
public class CategoryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CategoryApplicationService categoryApplicationService;

    CreateCategoryCommand validCommand;

    @BeforeEach
    void setUp() {
        // given
        validCommand = CreateCategoryCommand.builder()
                .name("Test category")
                .parentId(5L)
                .build();
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ADMIN"})
    void createCategory_WithAdminRole_ShouldReturnCreated() throws Exception {
        // given
        CreateCategoryResponse response = new CreateCategoryResponse("Test category", "Category is created");

        Mockito.when(categoryApplicationService.createCategory(any(CreateCategoryCommand.class)))
                .thenReturn(response);

        // when & then
        mockMvc.perform(post("/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validCommand)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test category"))
                .andExpect(jsonPath("$.message").value("Category is created"));
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void createCategory_WithCustomerRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCommand)))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ADMIN"})
    void createCategory_WithInvalidCommand_ShouldReturnBadRequest() throws Exception {
        // given
        CreateCategoryCommand invalidCommand = CreateCategoryCommand.builder()
                .name("") // invalid name
                .parentId(-1L)  // invalid parent category id
                .build();

        String content = objectMapper.writeValueAsString(invalidCommand);

        // when & then
        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}
