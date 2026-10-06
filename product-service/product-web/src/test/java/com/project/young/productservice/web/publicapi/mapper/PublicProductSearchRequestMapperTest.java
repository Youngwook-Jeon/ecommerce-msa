package com.project.young.productservice.web.publicapi.mapper;

import com.project.young.productservice.application.exception.ProductSearchRequestException;
import com.project.young.productservice.application.service.ProductSearchQueryValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.LinkedMultiValueMap;

import java.math.BigDecimal;
import java.util.List;

import static com.project.young.productservice.application.exception.ProductSearchRequestException.Code.INVALID_SEARCH_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicProductSearchRequestMapperTest {

    private final PublicProductSearchRequestMapper mapper = new PublicProductSearchRequestMapper();

    @Test
    void bindsRepeatedBrandsWithoutSplittingCommasAndPreservesPricePrecision() {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("q", " 이어폰 ");
        params.add("categoryId", "12");
        params.add("brands", "Apple, Samsung");
        params.add("brands", "Example");
        params.add("minPrice", "50.00");
        params.add("maxPrice", "200.00");
        params.add("sort", "price_asc");
        params.add("page", "1");
        params.add("size", "24");
        var query = mapper.toQuery(mapper.fromParameters(params));
        params.get("brands").clear();
        assertThat(query.brands()).containsExactly("Apple, Samsung", "Example");
        assertThat(query.minPrice()).isEqualTo(new BigDecimal("50.00"));
        var criteria = new ProductSearchQueryValidator().validate(query);
        assertThat(criteria.q()).isEqualTo("이어폰");
        assertThat(criteria.offset()).isEqualTo(24);
    }

    @Test
    void omittedScalarsRemainNullForApplicationDefaults() {
        var request = mapper.fromParameters(new LinkedMultiValueMap<>());
        assertThat(request.page()).isNull();
        assertThat(request.size()).isNull();
        assertThat(request.sort()).isNull();
        assertThat(request.categoryId()).isNull();
        assertThat(request.brands()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"q", "categoryId", "minPrice", "maxPrice", "sort", "page", "size"})
    void rejectsRepeatedScalarsEvenWhenValuesAreIdentical(String key) {
        var params = new LinkedMultiValueMap<String, String>();
        params.put(key, List.of("1", "1"));
        assertInvalid(params);
    }

    @ParameterizedTest
    @ValueSource(strings = {"keyword", "feature:connectivity", "facet", "unknown"})
    void rejectsUnknownParameters(String key) {
        var params = new LinkedMultiValueMap<String, String>();
        params.add(key, "value");
        assertInvalid(params);
    }

    @Test
    void rejectsMalformedNumbersEmptyValuesAndOverflow() {
        for (String key : List.of("categoryId", "page", "size")) {
            for (String value : List.of("", " ", "1.0", "1e2", "+1", "99999999999999999999999")) {
                var params = new LinkedMultiValueMap<String, String>();
                params.add(key, value);
                assertInvalid(params);
            }
        }
        for (String key : List.of("minPrice", "maxPrice")) {
            for (String value : List.of("", "NaN", "Infinity", "1e2", " 1 ", ".5", "1.")) {
                var params = new LinkedMultiValueMap<String, String>();
                params.add(key, value);
                assertInvalid(params);
            }
        }
    }

    private void assertInvalid(LinkedMultiValueMap<String, String> params) {
        assertThatThrownBy(() -> mapper.fromParameters(params))
                .isInstanceOfSatisfying(ProductSearchRequestException.class,
                        exception -> assertThat(exception.code()).isEqualTo(INVALID_SEARCH_REQUEST));
    }
}
