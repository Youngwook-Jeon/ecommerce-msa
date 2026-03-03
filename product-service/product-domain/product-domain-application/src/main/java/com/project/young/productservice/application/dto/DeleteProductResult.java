package com.project.young.productservice.application.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record DeleteProductResult(UUID id, String name) {
}
