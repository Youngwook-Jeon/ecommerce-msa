package com.project.young.productservice.application.dto.result;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;

@Builder
public record PresignProductImageUploadResult(
        String uploadUrl,
        String httpMethod,
        Map<String, String> headers,
        String objectKey,
        String publicUrl,
        Instant expiresAt
) {
    public PresignProductImageUploadResult {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
