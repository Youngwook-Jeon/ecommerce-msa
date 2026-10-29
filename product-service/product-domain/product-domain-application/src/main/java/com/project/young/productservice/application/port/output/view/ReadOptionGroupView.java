package com.project.young.productservice.application.port.output.view;

import com.project.young.productservice.domain.valueobject.OptionStatus;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ReadOptionGroupView(
        UUID id,
        UUID attributeId,
        long version,
        String name,
        String displayName,
        OptionStatus status,
        List<ReadOptionValueView> optionValues
) {
}
