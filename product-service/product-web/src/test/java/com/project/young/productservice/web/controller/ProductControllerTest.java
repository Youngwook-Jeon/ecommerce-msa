package com.project.young.productservice.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.young.productservice.domain.dto.CreateProductCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@ContextConfiguration(classes = TestConfig.class)
class ProductControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    CreateProductCommand validCommand;

    @BeforeEach
    void setUp() {
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
    void create_WithValidCommand_ShouldReturnOkStatus() throws Exception {
        String content = objectMapper.writeValueAsString(validCommand);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("product created"));
    }

    @Test
    void create_WithInvalidCommand_ShouldReturnBadRequest() throws Exception {
        CreateProductCommand invalidCommand = CreateProductCommand.builder()
                .productName("") // invalid name
                .description("test")  // invalid description (too short)
                .price(new BigDecimal("-1.00")) // invalid price
                .build();

        String content = objectMapper.writeValueAsString(invalidCommand);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}