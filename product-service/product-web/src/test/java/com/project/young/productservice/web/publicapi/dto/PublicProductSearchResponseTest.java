package com.project.young.productservice.web.publicapi.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublicProductSearchResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesCategoryAndDiscriminatedFacetsWithoutFieldsFromOtherTypes() throws Exception {
        var response = new PublicProductSearchResponse(
                new PublicProductSearchResponse.Query("이어폰", "relevance"),
                List.of(new PublicProductSearchResponse.Product(UUID.randomUUID(),
                        new PublicProductSearchResponse.Category(12, "이어폰"), "Wireless Earbuds", null,
                        null, new BigDecimal("89.99"))),
                0, 24, 1, 1,
                List.of(new PublicProductSearchFacetResponse.Terms("brand", "브랜드",
                                List.of(new PublicProductSearchFacetResponse.Value("example", "Example", 1)), false),
                        new PublicProductSearchFacetResponse.Range("price", "가격",
                                new BigDecimal("89.99"), new BigDecimal("89.99")),
                        new PublicProductSearchFacetResponse.Terms("feature:connectivity", "연결 방식",
                                List.of(new PublicProductSearchFacetResponse.Value("bluetooth", "블루투스", 1)), false)));
        var json = objectMapper.readTree(objectMapper.writeValueAsString(response));
        assertThat(json.at("/content/0/category/id").asLong()).isEqualTo(12);
        assertThat(json.at("/content/0/category/name").asText()).isEqualTo("이어폰");
        assertThat(json.at("/content/0/categoryId").isMissingNode()).isTrue();
        assertThat(json.at("/content/0/brand").isNull()).isTrue();
        assertThat(json.at("/content/0/mainImageUrl").isNull()).isTrue();
        assertThat(json.at("/facets").isArray()).isTrue();
        assertThat(json.at("/facets/0/type").asText()).isEqualTo("terms");
        assertThat(json.at("/facets/0/min").isMissingNode()).isTrue();
        assertThat(json.at("/facets/1/type").asText()).isEqualTo("range");
        assertThat(json.at("/facets/1/values").isMissingNode()).isTrue();
        assertThat(json.at("/facets/2/key").asText()).isEqualTo("feature:connectivity");
        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(response), PublicProductSearchResponse.class))
                .isEqualTo(response);
    }

    @Test
    void emptyResponseRetainsNullableFieldsAndBothFacets() throws Exception {
        var response = new PublicProductSearchResponse(new PublicProductSearchResponse.Query(null, "newest"),
                List.of(), 0, 24, 0, 0,
                List.of(new PublicProductSearchFacetResponse.Terms("brand", "브랜드", List.of(), false),
                        new PublicProductSearchFacetResponse.Range("price", "가격", null, null)));
        var json = objectMapper.readTree(objectMapper.writeValueAsString(response));
        assertThat(json.at("/query/q").isNull()).isTrue();
        assertThat(json.at("/content").size()).isZero();
        assertThat(json.at("/facets").size()).isEqualTo(2);
        assertThat(json.at("/facets/0/values").size()).isZero();
        assertThat(json.at("/facets/1/min").isNull()).isTrue();
        assertThat(json.at("/facets/1/max").isNull()).isTrue();
    }
}
