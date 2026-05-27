package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.query.PublicProductFacetQuery;
import com.project.young.productservice.application.dto.query.PublicProductFacetType;
import com.project.young.productservice.application.dto.result.PublicProductFacetResult;
import com.project.young.productservice.application.port.output.CategoryReadRepository;
import com.project.young.productservice.application.port.output.PublicProductFacetReadRepository;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PublicProductFacetQueryService {

    private final CategoryReadRepository categoryReadRepository;
    private final PublicProductFacetReadRepository publicProductFacetReadRepository;

    public PublicProductFacetQueryService(
            CategoryReadRepository categoryReadRepository,
            PublicProductFacetReadRepository publicProductFacetReadRepository
    ) {
        this.categoryReadRepository = categoryReadRepository;
        this.publicProductFacetReadRepository = publicProductFacetReadRepository;
    }

    public PublicProductFacetResult getFacets(PublicProductFacetQuery query) {
        PublicProductFacetQuery normalizedQuery = validate(query);

        if (!categoryReadRepository.existsActiveById(normalizedQuery.categoryId())) {
            throw new CategoryNotFoundException("Category not found: " + normalizedQuery.categoryId());
        }

        return publicProductFacetReadRepository.getFacets(normalizedQuery);
    }

    private static PublicProductFacetQuery validate(PublicProductFacetQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        if (query.categoryId() <= 0) {
            throw new IllegalArgumentException("categoryId must be a positive integer");
        }
        validatePriceRange(query.minPrice(), query.maxPrice());

        EnumSet<PublicProductFacetType> requested = EnumSet.noneOf(PublicProductFacetType.class);
        if (query.facets().isEmpty()) {
            requested.add(PublicProductFacetType.BRAND);
            requested.add(PublicProductFacetType.PRICE);
        } else {
            requested.addAll(query.facets());
        }

        List<String> normalizedBrands = query.brands().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        String normalizedKeyword = query.q() == null || query.q().isBlank() ? null : query.q().trim();

        return new PublicProductFacetQuery(
                query.categoryId(),
                normalizedKeyword,
                normalizedBrands,
                query.minPrice(),
                query.maxPrice(),
                List.copyOf(requested)
        );
    }

    private static void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null && minPrice.signum() < 0) {
            throw new IllegalArgumentException("minPrice must be >= 0");
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException("maxPrice must be >= 0");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("minPrice must be <= maxPrice");
        }
    }

}
