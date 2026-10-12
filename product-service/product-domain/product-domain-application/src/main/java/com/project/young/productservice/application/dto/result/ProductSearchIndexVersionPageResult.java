package com.project.young.productservice.application.dto.result;

import com.project.young.productservice.application.searchindex.model.ProductSearchIndexVersion;

import java.util.List;

public record ProductSearchIndexVersionPageResult(List<ProductSearchIndexVersion> content, int page, int size,
                                                 long totalElements) {
    public ProductSearchIndexVersionPageResult {
        content = List.copyOf(content);
    }

    public long totalPages() {
        return totalElements / size + (totalElements % size == 0 ? 0 : 1);
    }
}
