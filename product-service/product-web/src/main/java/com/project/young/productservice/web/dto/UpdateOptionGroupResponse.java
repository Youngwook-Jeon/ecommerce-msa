package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record UpdateOptionGroupResponse(UUID id, String name, String displayName, String status, String message) {
}
