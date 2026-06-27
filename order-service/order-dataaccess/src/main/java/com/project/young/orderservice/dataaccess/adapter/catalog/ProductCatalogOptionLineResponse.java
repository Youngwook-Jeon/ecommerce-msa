package com.project.young.orderservice.dataaccess.adapter.catalog;

import java.util.UUID;

public record ProductCatalogOptionLineResponse(
        int stepOrder,
        UUID productOptionGroupId,
        String optionGroupName,
        UUID productOptionValueId,
        String optionValueName
) {
}
