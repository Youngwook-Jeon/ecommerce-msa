package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ProductImageCommitResponse(
        UUID id,
        String publicUrl,
        String role,
        int sortOrder,
        String message
) {
}
