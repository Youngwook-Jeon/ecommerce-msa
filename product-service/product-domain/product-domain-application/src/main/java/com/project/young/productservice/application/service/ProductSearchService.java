package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.condition.ProductSearchCriteria;
import com.project.young.productservice.application.dto.query.ProductSearchQuery;
import com.project.young.productservice.application.dto.result.ProductSearchPageResult;
import com.project.young.productservice.application.dto.result.ProductSearchResult;
import com.project.young.productservice.application.exception.ProductSearchCategoryNotFoundException;
import com.project.young.productservice.application.port.output.CategoryReadRepository;
import com.project.young.productservice.application.port.output.ProductSearchPort;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchService {

    private final ProductSearchQueryValidator validator;
    private final CategoryReadRepository categoryReadRepository;
    private final ProductSearchPort productSearchPort;

    public ProductSearchService(ProductSearchQueryValidator validator,
                                CategoryReadRepository categoryReadRepository,
                                ProductSearchPort productSearchPort) {
        this.validator = validator;
        this.categoryReadRepository = categoryReadRepository;
        this.productSearchPort = productSearchPort;
    }

    public ProductSearchPageResult search(ProductSearchQuery query) {
        ProductSearchCriteria criteria = validator.validate(query);
        if (criteria.categoryId() != null && !categoryReadRepository.existsActiveById(criteria.categoryId())) {
            throw new ProductSearchCategoryNotFoundException(criteria.categoryId());
        }
        ProductSearchResult result = productSearchPort.search(criteria);
        long totalPages = result.totalElements() / criteria.size()
                + (result.totalElements() % criteria.size() == 0 ? 0 : 1);
        return new ProductSearchPageResult(criteria, result, totalPages);
    }
}
