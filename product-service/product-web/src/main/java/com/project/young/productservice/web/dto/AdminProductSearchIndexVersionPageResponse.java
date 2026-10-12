package com.project.young.productservice.web.dto;

import java.util.List;

public record AdminProductSearchIndexVersionPageResponse(List<AdminProductSearchIndexVersionResponse> content,
                                                        int page, int size, long totalElements, long totalPages) {
    public AdminProductSearchIndexVersionPageResponse {
        content = List.copyOf(content);
    }
}
