package com.project.young.productservice.web.publicapi.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record PublicProductPageResponse(
        List<PublicProductSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public PublicProductPageResponse {
        content = content == null ? List.of() : content;
    }

    public static PublicProductPageResponse empty(int page, int size) {
        return PublicProductPageResponse.builder()
                .content(List.of())
                .page(page)
                .size(size)
                .totalElements(0L)
                .totalPages(0)
                .build();
    }
}
