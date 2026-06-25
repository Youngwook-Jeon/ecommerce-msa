package com.project.young.productservice.application.port.output.view;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ReadCartCatalogOptionLineView(
        int stepOrder,
        UUID productOptionGroupId,
        String optionGroupName,
        UUID productOptionValueId,
        String optionValueName
) {
}
