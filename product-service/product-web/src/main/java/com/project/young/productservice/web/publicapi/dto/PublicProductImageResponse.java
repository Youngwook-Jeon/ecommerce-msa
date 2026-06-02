package com.project.young.productservice.web.publicapi.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record PublicProductImageResponse(
        UUID id,
        String url,
        String role,
        int sortOrder
) {
}
