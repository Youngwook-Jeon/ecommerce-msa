package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record DeleteOptionValueResponse(UUID id, String value, String message) {
}
