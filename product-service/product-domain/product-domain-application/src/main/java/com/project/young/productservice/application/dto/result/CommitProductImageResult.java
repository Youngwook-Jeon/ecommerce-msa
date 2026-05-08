package com.project.young.productservice.application.dto.result;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CommitProductImageResult(
        UUID id,
        String publicUrl,
        String role,
        int sortOrder
) {
}
