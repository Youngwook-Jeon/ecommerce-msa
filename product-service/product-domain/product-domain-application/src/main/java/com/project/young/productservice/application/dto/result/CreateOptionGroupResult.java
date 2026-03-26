package com.project.young.productservice.application.dto.result;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CreateOptionGroupResult(UUID id, String name) {
}
