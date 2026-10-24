package com.project.young.productservice.application.port.output.view;

import com.project.young.productservice.domain.valueobject.AttributeDataType;

import java.util.UUID;

public record ReadCategoryOptionGroupView(
        UUID id,
        String name,
        String displayName,
        UUID attributeId,
        String attributeCode,
        String attributeDisplayName,
        AttributeDataType attributeDataType,
        String canonicalUnit,
        boolean attributeRequired,
        int displayOrder
) {
}
