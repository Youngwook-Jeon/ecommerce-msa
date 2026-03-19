package com.project.young.productservice.application.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record DeleteOptionValueResult(UUID id, String value) {
}
