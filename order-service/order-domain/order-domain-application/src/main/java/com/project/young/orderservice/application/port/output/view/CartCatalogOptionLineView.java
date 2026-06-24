package com.project.young.orderservice.application.port.output.view;

import java.util.UUID;

public record CartCatalogOptionLineView(
        int stepOrder,
        UUID productOptionGroupId,
        String optionGroupName,
        UUID productOptionValueId,
        String optionValueName
) {
}
