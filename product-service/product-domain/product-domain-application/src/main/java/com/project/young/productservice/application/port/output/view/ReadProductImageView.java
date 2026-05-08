package com.project.young.productservice.application.port.output.view;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ReadProductImageView(
        UUID id,
        String publicUrl,
        String role,
        String status,
        int sortOrder
) {
}
