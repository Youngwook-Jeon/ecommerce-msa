package com.project.young.productservice.web.publicapi.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record PublicCartCatalogLinesResponse(
        List<PublicCartCatalogLineResponse> lines
) {
    public PublicCartCatalogLinesResponse {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
