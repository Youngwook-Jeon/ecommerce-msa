package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.condition.PublicProductSearchCondition;
import com.project.young.productservice.application.dto.query.PublicProductListQuery;
import com.project.young.productservice.application.dto.query.PublicProductSort;
import com.project.young.productservice.application.dto.result.PublicProductListPageResult;
import com.project.young.productservice.application.port.output.CategoryReadRepository;
import com.project.young.productservice.application.port.output.PublicProductReadRepository;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional(readOnly = true)
public class PublicProductQueryService {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 24;
    public static final int MAX_SIZE = 48;

    private final CategoryReadRepository categoryReadRepository;
    private final PublicProductReadRepository publicProductReadRepository;

    public PublicProductQueryService(CategoryReadRepository categoryReadRepository,
                                     PublicProductReadRepository publicProductReadRepository) {
        this.categoryReadRepository = categoryReadRepository;
        this.publicProductReadRepository = publicProductReadRepository;
    }

    public PublicProductListPageResult listProductsByCategory(PublicProductListQuery query) {
        ValidatedListCriteria criteria = validateListQuery(query);

        if (!categoryReadRepository.existsActiveById(criteria.condition().categoryId())) {
            throw new CategoryNotFoundException("Category not found: " + criteria.condition().categoryId());
        }

        return publicProductReadRepository.search(
                criteria.condition(),
                criteria.sort(),
                criteria.page(),
                criteria.size()
        );
    }

    private ValidatedListCriteria validateListQuery(PublicProductListQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }

        long categoryId = query.categoryId();
        if (categoryId <= 0) {
            throw new IllegalArgumentException("categoryId must be a positive integer");
        }

        int page = query.page();
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }

        int size = normalizeSize(query.size());
        PublicProductSort sort = PublicProductSort.fromApiValue(query.sort());
        validatePriceRange(query.minPrice(), query.maxPrice());

        PublicProductSearchCondition condition = new PublicProductSearchCondition(
                categoryId,
                query.q(),
                query.brand(),
                query.minPrice(),
                query.maxPrice()
        );

        return new ValidatedListCriteria(condition, sort, page, size);
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

    private record ValidatedListCriteria(
            PublicProductSearchCondition condition,
            PublicProductSort sort,
            int page,
            int size
    ) {
    }

    static int normalizeSize(int requestedSize) {
        if (requestedSize < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }
        return Math.min(requestedSize, MAX_SIZE);
    }
}
