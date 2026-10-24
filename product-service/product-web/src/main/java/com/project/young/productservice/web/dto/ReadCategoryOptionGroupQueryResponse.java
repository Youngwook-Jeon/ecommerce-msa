package com.project.young.productservice.web.dto;

import java.util.UUID;

public record ReadCategoryOptionGroupQueryResponse(
        UUID id,
        String name,
        String displayName,
        UUID attributeId,
        String attributeCode,
        String attributeDisplayName,
        String attributeDataType,
        String canonicalUnit,
        boolean attributeRequired,
        int displayOrder
) {
}
