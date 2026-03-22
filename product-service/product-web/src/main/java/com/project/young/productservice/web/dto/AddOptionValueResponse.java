package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AddOptionValueResponse(UUID id, String value, String message) {
}
