package com.project.young.productservice.web.publicapi.mapper;

import com.project.young.productservice.application.dto.query.ProductSearchQuery;
import com.project.young.productservice.application.exception.ProductSearchRequestException;
import com.project.young.productservice.web.publicapi.dto.PublicProductSearchRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.project.young.productservice.application.exception.ProductSearchRequestException.Code.INVALID_SEARCH_REQUEST;

/** Bind the complete parameter map so unknown and repeated scalar parameters cannot be silently ignored. */
@Component
public class PublicProductSearchRequestMapper {

    private static final Set<String> PARAMETERS = Set.of(
            "q", "categoryId", "brands", "minPrice", "maxPrice", "sort", "page", "size");

    public PublicProductSearchRequest fromParameters(MultiValueMap<String, String> parameters) {
        if (parameters == null) {
            throw invalid("Request parameters are required");
        }
        parameters.forEach((key, values) -> {
            if (!PARAMETERS.contains(key) || values == null || values.isEmpty()
                    || values.stream().anyMatch(Objects::isNull)
                    || (!key.equals("brands") && values.size() != 1)) {
                throw invalid("Unknown or repeated parameter: " + key);
            }
        });
        try {
            return new PublicProductSearchRequest(
                    parameters.getFirst("q"),
                    longValue(parameters.getFirst("categoryId")),
                    parameters.getOrDefault("brands", List.of()),
                    priceValue(parameters.getFirst("minPrice")),
                    priceValue(parameters.getFirst("maxPrice")),
                    parameters.getFirst("sort"),
                    integerValue(parameters.getFirst("page")),
                    integerValue(parameters.getFirst("size"))
            );
        } catch (NumberFormatException exception) {
            throw invalid("Invalid numeric parameter");
        }
    }

    public ProductSearchQuery toQuery(PublicProductSearchRequest request) {
        return new ProductSearchQuery(request.q(), request.categoryId(), request.brands(),
                request.minPrice(), request.maxPrice(), request.sort(), request.page(), request.size());
    }

    private static Long longValue(String value) {
        requireInteger(value);
        return value == null ? null : Long.valueOf(value);
    }

    private static Integer integerValue(String value) {
        requireInteger(value);
        return value == null ? null : Integer.valueOf(value);
    }

    private static void requireInteger(String value) {
        if (value != null && !value.matches("-?[0-9]+")) {
            throw invalid("Expected an integer");
        }
    }

    private static BigDecimal priceValue(String value) {
        if (value == null) {
            return null;
        }
        if (!value.matches("-?[0-9]+(?:\\.[0-9]+)?")) {
            throw invalid("Expected a decimal price");
        }
        return new BigDecimal(value);
    }

    private static ProductSearchRequestException invalid(String message) {
        return new ProductSearchRequestException(INVALID_SEARCH_REQUEST, message);
    }
}
