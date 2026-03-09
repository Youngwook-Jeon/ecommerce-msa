package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record AdminProductPageResponse(
        List<AdminProductListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public AdminProductPageResponse {
        content = content == null ? List.of() : content;
    }
}