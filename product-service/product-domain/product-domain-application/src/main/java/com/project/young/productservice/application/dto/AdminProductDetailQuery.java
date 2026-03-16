package com.project.young.productservice.application.dto;

import jakarta.validation.constraints.NotNull;import lombok.Builder;

import java.util.UUID;

@Builder
public record AdminProductDetailQuery(@NotNull UUID id) {
}
