package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.query.ProductSearchQuery;
import com.project.young.productservice.application.dto.query.PublicProductSort;
import com.project.young.productservice.application.exception.ProductSearchRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import static com.project.young.productservice.application.exception.ProductSearchRequestException.Code.INVALID_SEARCH_REQUEST;
import static com.project.young.productservice.application.exception.ProductSearchRequestException.Code.SEARCH_PAGE_LIMIT_EXCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSearchQueryValidatorTest {

    private final ProductSearchQueryValidator validator = new ProductSearchQueryValidator();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t\n", "\u00a0\u2003"})
    void browseDefaultsApplyToMissingOrUnicodeBlankQuery(String q) {
        var criteria = validator.validate(query(q, null, null, null, null, null, null, null));
        assertThat(criteria.q()).isNull();
        assertThat(criteria.categoryId()).isNull();
        assertThat(criteria.brands()).isEmpty();
        assertThat(criteria.sort()).isEqualTo(PublicProductSort.NEWEST);
        assertThat(criteria.page()).isZero();
        assertThat(criteria.size()).isEqualTo(24);
    }

    @Test
    void normalizesNfcAndUnicodeWhitespaceAndDefaultsSearchToRelevance() {
        var criteria = validator.validate(query("\u00a0가\t\n 무선\u2003이어폰 ", 12L,
                List.of(" Apple ", "APPLE", "\u00a0", "Samsung", "가", "가"),
                null, null, null, null, null));
        assertThat(criteria.q()).isEqualTo("가 무선 이어폰");
        assertThat(criteria.categoryId()).isEqualTo(12);
        assertThat(criteria.brands()).containsExactly("apple", "samsung", "가");
        assertThat(criteria.sort()).isEqualTo(PublicProductSort.RELEVANCE);
    }

    @Test
    void brandKeysAreIndependentOfDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(validator.validate(query(null, null, List.of("IKEA"), null, null,
                    null, null, null)).brands()).containsExactly("ikea");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void lengthsCountUnicodeCodePointsAfterNormalization() {
        String emoji = "😀";
        assertThat(validator.validate(query(emoji.repeat(200), null, List.of(emoji.repeat(100)),
                null, null, null, null, null)).q()).hasSize(400);
        assertInvalid(query(emoji.repeat(201), null, null, null, null, null, null, null));
        assertInvalid(query(null, null, List.of(emoji.repeat(101)), null, null, null, null, null));
        assertInvalid(query(null, null, List.of("İ".repeat(51)), null, null, null, null, null));
    }

    @Test
    void brandLimitAppliesAfterDeduplicationAndInputIsDefensivelyCopied() {
        List<String> brands = new ArrayList<>(IntStream.range(0, 20).mapToObj(i -> "Brand" + i).toList());
        brands.add(" BRAND0 ");
        ProductSearchQuery query = query(null, null, brands, null, null, null, null, null);
        brands.clear();
        assertThat(validator.validate(query).brands()).hasSize(20);
        assertInvalid(query(null, null, IntStream.range(0, 21).mapToObj(i -> "Brand" + i).toList(),
                null, null, null, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"newest", "price_asc", "price_desc", "relevance"})
    void acceptsExplicitSortWithSearchTerm(String sort) {
        assertThat(validator.validate(query("이어폰", null, null, null, null, sort, null, null))
                .sort().apiValue()).isEqualTo(sort);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "RELEVANCE", " newest", "unknown"})
    void rejectsNonContractSortValues(String sort) {
        assertInvalid(query("이어폰", null, null, null, null, sort, null, null));
    }

    @Test
    void relevanceRequiresNormalizedSearchTerm() {
        assertInvalid(query("\u00a0", null, null, null, null, "relevance", null, null));
    }

    @Test
    void acceptsEqualPriceBoundsAndRejectsNegativePrecisionAndReversedBounds() {
        var criteria = validator.validate(query(null, null, null, new BigDecimal("0.00"),
                new BigDecimal("0.00"), null, null, null));
        assertThat(criteria.minPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(criteria.maxPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        for (String invalid : List.of("-0.01", "1.001", "1.000")) {
            assertInvalid(query(null, null, null, new BigDecimal(invalid), null, null, null, null));
            assertInvalid(query(null, null, null, null, new BigDecimal(invalid), null, null, null));
        }
        assertInvalid(query(null, null, null, BigDecimal.TEN, BigDecimal.ONE, null, null, null));
    }

    @Test
    void validatesCategoryAndPageSizeWithoutClamping() {
        assertInvalid(query(null, 0L, null, null, null, null, null, null));
        assertInvalid(query(null, -1L, null, null, null, null, null, null));
        assertInvalid(query(null, null, null, null, null, null, -1, null));
        assertInvalid(query(null, null, null, null, null, null, null, 0));
        assertInvalid(query(null, null, null, null, null, null, null, 49));
        assertThat(validator.validate(query(null, Long.MAX_VALUE, null, null, null, null, 0, 48))
                .size()).isEqualTo(48);
    }

    @Test
    void windowIncludesRequestedSizeAndDoesNotOverflow() {
        assertThat(validator.validate(query(null, null, null, null, null, null, 399, 25))
                .offset()).isEqualTo(9975);
        for (int page : List.of(400, Integer.MAX_VALUE)) {
            assertThatThrownBy(() -> validator.validate(query(null, null, null, null, null, null, page, 25)))
                    .isInstanceOfSatisfying(ProductSearchRequestException.class,
                            exception -> assertThat(exception.code()).isEqualTo(SEARCH_PAGE_LIMIT_EXCEEDED));
        }
    }

    @Test
    void missingQueryIsAnInvalidRequest() {
        assertInvalid(null);
    }

    private void assertInvalid(ProductSearchQuery query) {
        assertThatThrownBy(() -> validator.validate(query))
                .isInstanceOfSatisfying(ProductSearchRequestException.class,
                        exception -> assertThat(exception.code()).isEqualTo(INVALID_SEARCH_REQUEST));
    }

    private static ProductSearchQuery query(String q, Long categoryId, List<String> brands,
                                           BigDecimal min, BigDecimal max, String sort, Integer page, Integer size) {
        return new ProductSearchQuery(q, categoryId, brands, min, max, sort, page, size);
    }
}
