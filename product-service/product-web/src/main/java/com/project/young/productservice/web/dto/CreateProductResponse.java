package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CreateProductResponse(UUID id, String name, String message) {
}
