package com.project.young.productservice.domain.dto;

import lombok.Builder;

@Builder
public record CreateProductResponse(String productName, String message) {
}
