package com.project.young.productservice.application.dto.result;

import com.project.young.productservice.application.dto.condition.ProductSearchCriteria;

public record ProductSearchPageResult(ProductSearchCriteria criteria, ProductSearchResult result, long totalPages) {
}
