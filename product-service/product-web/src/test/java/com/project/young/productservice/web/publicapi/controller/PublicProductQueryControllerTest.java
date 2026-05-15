package com.project.young.productservice.web.publicapi.controller;

import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.controller.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicProductQueryController.class)
@Import({SecurityConfig.class, TestConfig.class})
class PublicProductQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /public/products — unauthenticated returns empty page")
    void listProducts_withoutAuth_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/public/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    @DisplayName("GET /public/products — honors page and size query params")
    void listProducts_withPagination_returnsRequestedPageMetadata() throws Exception {
        mockMvc.perform(get("/public/products").param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10));
    }
}
