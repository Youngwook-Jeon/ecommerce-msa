package com.project.young.productservice.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.domain.dto.CreateProductCommand;
import com.project.young.productservice.domain.dto.CreateProductResponse;
import com.project.young.productservice.domain.ports.input.service.ProductApplicationService;
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

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, TestConfig.class})
class ProductControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ProductApplicationService productApplicationService;

    CreateProductCommand validCommand;

    @BeforeEach
    void setUp() {
        // given
        validCommand = CreateProductCommand.builder()
                .productName("Test Product")
                .description("Test Description for the product")
                .price(new BigDecimal("100.00"))
                .build();
    }

    @Test
    void getAll_ShouldReturnOkStatus() throws Exception {
        mockMvc.perform(get("/products"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("products"));
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ADMIN"})
    void createProduct_WithAdminRole_ShouldReturnCreated() throws Exception {
        // given
        CreateProductResponse response = new CreateProductResponse("Test Product", "Product is created");

        Mockito.when(productApplicationService.createProduct(any(CreateProductCommand.class)))
                .thenReturn(response);

        // when & then
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCommand)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productName").value("Test Product"))
                .andExpect(jsonPath("$.message").value("Product is created"));
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void createProduct_WithCustomerRole_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCommand)))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ADMIN"})
    void create_WithInvalidCommand_ShouldReturnBadRequest() throws Exception {
        // given
        CreateProductCommand invalidCommand = CreateProductCommand.builder()
                .productName("") // invalid name
                .description("test")  // invalid description (too short)
                .price(new BigDecimal("-1.00")) // invalid price
                .build();

        String content = objectMapper.writeValueAsString(invalidCommand);

        // when & then
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}