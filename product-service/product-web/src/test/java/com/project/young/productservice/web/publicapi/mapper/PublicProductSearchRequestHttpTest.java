package com.project.young.productservice.web.publicapi.mapper;

import com.project.young.productservice.application.dto.condition.ProductSearchCriteria;
import com.project.young.productservice.application.service.ProductSearchQueryValidator;
import com.project.young.productservice.web.exception.handler.ProductServiceGlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A test-only binding endpoint; the production search endpoint is added with the search use case. */
class PublicProductSearchRequestHttpTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BindingController())
            .setControllerAdvice(new ProductServiceGlobalExceptionHandler()).build();

    @Test
    void bindsAllRepeatedValuesBeforeApplicationValidation() throws Exception {
        mockMvc.perform(get("/test-search").param("q", " 이어폰 ")
                        .param("brands", "APPLE", "Samsung").param("categoryId", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.q").value("이어폰"))
                .andExpect(jsonPath("$.brands[0]").value("apple"))
                .andExpect(jsonPath("$.brands[1]").value("samsung"));
    }

    @Test
    void returnsContractErrorForRepeatedUnknownAndInvalidParameters() throws Exception {
        mockMvc.perform(get("/test-search").param("q", "a", "b"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_REQUEST"));
        mockMvc.perform(get("/test-search").param("unknown", "value"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_REQUEST"));
        mockMvc.perform(get("/test-search").param("minPrice", "1.001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_REQUEST"));
    }

    @Test
    void returnsDistinctErrorForPageWindowLimit() throws Exception {
        mockMvc.perform(get("/test-search").param("page", "400").param("size", "25"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_PAGE_LIMIT_EXCEEDED"));
    }

    @RestController
    static class BindingController {
        private final PublicProductSearchRequestMapper mapper = new PublicProductSearchRequestMapper();
        private final ProductSearchQueryValidator validator = new ProductSearchQueryValidator();

        @GetMapping("/test-search")
        ProductSearchCriteria bind(@RequestParam MultiValueMap<String, String> parameters) {
            return validator.validate(mapper.toQuery(mapper.fromParameters(parameters)));
        }
    }
}
