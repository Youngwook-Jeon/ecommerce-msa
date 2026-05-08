package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ReadProductImageResponse(
        UUID id,
        String publicUrl,
        String role,
        String status,
        int sortOrder
) {
}
