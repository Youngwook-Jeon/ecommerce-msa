package com.project.young.productservice.application.port.output.view;

import com.project.young.productservice.domain.valueobject.OptionStatus;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ReadOptionValueView(
        UUID id,
        UUID attributeValueId,
        String value,
        String displayName,
        int sortOrder,
        OptionStatus status
) {
}
