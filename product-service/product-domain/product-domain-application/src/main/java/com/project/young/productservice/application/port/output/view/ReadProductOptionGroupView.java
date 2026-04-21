package com.project.young.productservice.application.port.output.view;

import lombok.Builder;
import com.project.young.productservice.domain.valueobject.OptionStatus;

import java.util.List;
import java.util.UUID;

@Builder
public record ReadProductOptionGroupView(
        UUID productOptionGroupId,
        UUID optionGroupId,
        int stepOrder,
        boolean required,
        OptionStatus status,
        List<ReadProductOptionValueView> optionValues
) {
    public ReadProductOptionGroupView {
        optionValues = optionValues == null ? List.of() : optionValues;
    }
}
