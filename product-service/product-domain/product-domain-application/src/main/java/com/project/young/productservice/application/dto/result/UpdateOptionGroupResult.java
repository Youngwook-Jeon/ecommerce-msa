package com.project.young.productservice.application.dto.result;

import com.project.young.productservice.domain.valueobject.OptionStatus;
import lombok.Builder;

import java.util.UUID;

@Builder
public record UpdateOptionGroupResult(
        UUID id,
        String name,
        String displayName,
        OptionStatus status
) {
}