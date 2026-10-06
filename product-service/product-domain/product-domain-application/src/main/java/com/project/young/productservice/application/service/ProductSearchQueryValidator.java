package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.condition.ProductSearchCriteria;
import com.project.young.productservice.application.dto.query.ProductSearchQuery;
import com.project.young.productservice.application.dto.query.PublicProductSort;
import com.project.young.productservice.application.exception.ProductSearchRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static com.project.young.productservice.application.exception.ProductSearchRequestException.Code.INVALID_SEARCH_REQUEST;
import static com.project.young.productservice.application.exception.ProductSearchRequestException.Code.SEARCH_PAGE_LIMIT_EXCEEDED;

@Component
public class ProductSearchQueryValidator {

    public static final int DEFAULT_SIZE = 24;
    public static final int MAX_SIZE = 48;
    public static final int MAX_WINDOW = 10000;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern EDGE_WHITESPACE = Pattern.compile("^\\s+|\\s+$", Pattern.UNICODE_CHARACTER_CLASS);

    public ProductSearchCriteria validate(ProductSearchQuery query) {
        if (query == null) {
            throw invalid("Search query is required");
        }
        String q = normalize(query.q());
        if (q != null) {
            q = WHITESPACE.matcher(q).replaceAll(" ");
            checkLength(q, 200, "q");
        }
        if (query.categoryId() != null && query.categoryId() <= 0) {
            throw invalid("categoryId must be positive");
        }

        Set<String> brands = new LinkedHashSet<>();
        for (String brand : query.brands()) {
            String normalized = normalize(brand);
            if (normalized != null) {
                normalized = normalize(normalized.toLowerCase(Locale.ROOT));
                checkLength(normalized, 100, "brands");
                brands.add(normalized);
            }
        }
        if (brands.size() > 20) {
            throw invalid("At most 20 distinct brands are allowed");
        }

        validatePrice(query.minPrice(), "minPrice");
        validatePrice(query.maxPrice(), "maxPrice");
        if (query.minPrice() != null && query.maxPrice() != null
                && query.minPrice().compareTo(query.maxPrice()) > 0) {
            throw invalid("minPrice must not exceed maxPrice");
        }

        PublicProductSort sort = query.sort() == null
                ? (q == null ? PublicProductSort.NEWEST : PublicProductSort.RELEVANCE)
                : Arrays.stream(PublicProductSort.values())
                        .filter(value -> value.apiValue().equals(query.sort()))
                        .findFirst().orElseThrow(() -> invalid("Unknown sort"));
        if (q == null && sort == PublicProductSort.RELEVANCE) {
            throw invalid("relevance requires a search term");
        }

        int page = query.page() == null ? 0 : query.page();
        int size = query.size() == null ? DEFAULT_SIZE : query.size();
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw invalid("page must be nonnegative and size must be between 1 and 48");
        }
        if ((long) page * size + size > MAX_WINDOW) {
            throw new ProductSearchRequestException(SEARCH_PAGE_LIMIT_EXCEEDED, "Search page limit exceeded");
        }
        return new ProductSearchCriteria(q, query.categoryId(), List.copyOf(brands),
                query.minPrice(), query.maxPrice(), sort, page, size);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        normalized = EDGE_WHITESPACE.matcher(normalized).replaceAll("");
        return normalized.isEmpty() ? null : normalized;
    }

    private static void checkLength(String value, int max, String field) {
        if (value.codePointCount(0, value.length()) > max) {
            throw invalid(field + " is too long");
        }
    }

    private static void validatePrice(BigDecimal price, String field) {
        if (price != null && (price.signum() < 0 || price.scale() > 2)) {
            throw invalid(field + " must be nonnegative with at most two decimal places");
        }
    }

    private static ProductSearchRequestException invalid(String message) {
        return new ProductSearchRequestException(INVALID_SEARCH_REQUEST, message);
    }
}
