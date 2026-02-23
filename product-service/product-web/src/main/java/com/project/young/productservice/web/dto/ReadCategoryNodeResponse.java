package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ReadCategoryNodeResponse(Long id, String name, Long parentId, String status,
                                       List<ReadCategoryNodeResponse> children) {

    public ReadCategoryNodeResponse {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
