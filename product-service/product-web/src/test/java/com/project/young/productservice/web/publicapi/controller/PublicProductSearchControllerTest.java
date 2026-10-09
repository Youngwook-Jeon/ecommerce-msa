package com.project.young.productservice.web.publicapi.controller;

import com.project.young.productservice.application.dto.condition.ProductSearchCriteria;
import com.project.young.productservice.application.dto.result.ProductSearchResult;
import com.project.young.productservice.application.exception.ProductSearchUnavailableException;
import com.project.young.productservice.application.port.output.CategoryReadRepository;
import com.project.young.productservice.application.port.output.ProductSearchPort;
import com.project.young.productservice.application.service.ProductSearchQueryValidator;
import com.project.young.productservice.application.service.ProductSearchService;
import com.project.young.productservice.application.service.PublicProductQueryService;
import com.project.young.productservice.web.config.SecurityConfig;
import com.project.young.productservice.web.controller.TestConfig;
import com.project.young.productservice.web.exception.handler.ProductSearchExceptionHandler;
import com.project.young.productservice.web.exception.handler.ProductServiceGlobalExceptionHandler;
import com.project.young.productservice.web.publicapi.mapper.PublicProductQueryResponseMapper;
import com.project.young.productservice.web.publicapi.mapper.PublicProductSearchRequestMapper;
import com.project.young.productservice.web.publicapi.mapper.PublicProductSearchResponseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PublicProductSearchController.class, PublicProductQueryController.class})
@Import({SecurityConfig.class, TestConfig.class, ProductSearchService.class, ProductSearchQueryValidator.class,
        PublicProductSearchRequestMapper.class, PublicProductSearchResponseMapper.class,
        ProductServiceGlobalExceptionHandler.class, ProductSearchExceptionHandler.class})
class PublicProductSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ProductSearchPort searchPort;
    @MockitoBean
    private CategoryReadRepository categoryReadRepository;
    @MockitoBean
    private PublicProductQueryService existingQueryService;
    @MockitoBean
    private PublicProductQueryResponseMapper existingResponseMapper;

    @Test
    void anonymousSearchUsesLiteralRouteAndMapsProductsTotalsAndFacets() throws Exception {
        UUID id = UUID.randomUUID();
        when(searchPort.search(any())).thenAnswer(invocation -> {
            ProductSearchCriteria criteria = invocation.getArgument(0);
            org.assertj.core.api.Assertions.assertThat(criteria.brands()).containsExactly("example");
            return new ProductSearchResult(
                    List.of(new ProductSearchResult.Product(id, 12, "이어폰", "Wireless Earbuds", "Example",
                            null, new BigDecimal("89.99"))),
                    25, List.of(new ProductSearchResult.Brand("example", "Example", 25)), false,
                    new BigDecimal("89.99"), new BigDecimal("199.99"));
        });
        mockMvc.perform(get("/public/products/search").param("q", " 무선\t이어폰 ")
                        .param("brands", "EXAMPLE", "Example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query.q").value("무선 이어폰"))
                .andExpect(jsonPath("$.query.sort").value("relevance"))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].category.name").value("이어폰"))
                .andExpect(jsonPath("$.content[0].category.id").value(12))
                .andExpect(jsonPath("$.content[0].categoryId").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.facets[0].type").value("terms"))
                .andExpect(jsonPath("$.facets[0].values[0].count").value(25))
                .andExpect(jsonPath("$.facets[1].type").value("range"))
                .andExpect(jsonPath("$.facets[1].max").value(199.99));
        verifyNoInteractions(existingQueryService, existingResponseMapper, categoryReadRepository);
    }

    @Test
    void emptyCategoryBrowseKeepsBothFacetsAndNewestDefault() throws Exception {
        when(categoryReadRepository.existsActiveById(12)).thenReturn(true);
        when(searchPort.search(any())).thenReturn(new ProductSearchResult(List.of(), 0, List.of(), false, null, null));
        mockMvc.perform(get("/public/products/search").param("categoryId", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query.sort").value("newest"))
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.facets.length()").value(2))
                .andExpect(jsonPath("$.facets[0].values").isEmpty())
                .andExpect(content().string(containsString("\"min\":null")));
    }

    @ParameterizedTest
    @CsvSource({"size,49", "unknown,value", "sort,relevance", "minPrice,1.001", "categoryId,0"})
    void invalidRequestNeverCallsPorts(String key, String value) throws Exception {
        mockMvc.perform(get("/public/products/search").param(key, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_REQUEST"));
        verifyNoInteractions(searchPort, categoryReadRepository);
    }

    @Test
    void repeatedScalarIsRejected() throws Exception {
        mockMvc.perform(get("/public/products/search").param("q", "a", "b"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_REQUEST"));
        verifyNoInteractions(searchPort, categoryReadRepository);
    }

    @Test
    void pageLimitHasDistinctErrorCode() throws Exception {
        mockMvc.perform(get("/public/products/search").param("page", "400").param("size", "25"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_PAGE_LIMIT_EXCEEDED"));
    }

    @Test
    void unknownOrInactiveCategoryReturns404BeforeSearch() throws Exception {
        mockMvc.perform(get("/public/products/search").param("categoryId", "12"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        verifyNoInteractions(searchPort);
    }

    @Test
    void engineFailureReturns503WithoutInternalDetails() throws Exception {
        when(searchPort.search(any())).thenThrow(new ProductSearchUnavailableException("internal-host:9200 timeout"));
        mockMvc.perform(get("/public/products/search"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SEARCH_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("internal-host"))));
    }

    @Test
    void unexpectedFailureUsesSearchSpecific500Code() throws Exception {
        when(searchPort.search(any())).thenThrow(new IllegalStateException("internal details"));
        mockMvc.perform(get("/public/products/search"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("internal details"))));
    }

    @Test
    void adapterArgumentErrorIsNotMisclassifiedAsInvalidClientInput() throws Exception {
        when(searchPort.search(any())).thenThrow(new IllegalArgumentException("Invalid indexed data"));
        mockMvc.perform(get("/public/products/search"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
