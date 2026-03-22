package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record UpdateOptionValueResponse(UUID id, String value, String displayName, int sortOrder, String status, String message) {
}
