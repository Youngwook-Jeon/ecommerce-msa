package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ReadCategoryResponse(List<ReadCategoryNodeResponse> categories) {
    public ReadCategoryResponse {
        categories = categories == null ? List.of() : categories;
    }
}
