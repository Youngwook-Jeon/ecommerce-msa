package com.project.young.productservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Validated representation of the required Idempotency-Key header on build/activation requests. */
public record AdminProductSearchIndexRequestHeaders(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,128}") String idempotencyKey
) {
}
