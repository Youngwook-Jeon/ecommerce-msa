package com.project.young.productservice.application.dto;

import com.project.young.productservice.domain.valueobject.OptionStatus;
import lombok.Builder;

import java.util.UUID;

@Builder
public record UpdateOptionValueResult(
        UUID id,
        String value,
        String displayName,
        int sortOrder,
        OptionStatus status
) {
}