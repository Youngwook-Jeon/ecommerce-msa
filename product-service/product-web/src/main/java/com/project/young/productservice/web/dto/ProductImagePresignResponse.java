package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;

@Builder
public record ProductImagePresignResponse(
        String uploadUrl,
        String httpMethod,
        Map<String, String> headers,
        String objectKey,
        String publicUrl,
        Instant expiresAt
) {
}
