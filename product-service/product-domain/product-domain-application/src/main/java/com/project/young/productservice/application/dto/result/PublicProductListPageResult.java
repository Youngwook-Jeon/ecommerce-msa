package com.project.young.productservice.application.dto.result;

import com.project.young.productservice.application.port.output.view.ReadPublicProductSummaryView;

import java.util.List;

public record PublicProductListPageResult(
        List<ReadPublicProductSummaryView> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public PublicProductListPageResult {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
