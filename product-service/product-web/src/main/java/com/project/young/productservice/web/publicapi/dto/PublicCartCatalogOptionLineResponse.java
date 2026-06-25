package com.project.young.productservice.web.publicapi.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record PublicCartCatalogOptionLineResponse(
        int stepOrder,
        UUID productOptionGroupId,
        String optionGroupName,
        UUID productOptionValueId,
        String optionValueName
) {
}
