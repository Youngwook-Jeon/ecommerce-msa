package com.project.young.productservice.application.port.output.view;

import com.project.young.productservice.domain.valueobject.AttributeDataType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReadCategoryOptionValueView(
        UUID optionGroupId,
        UUID id,
        String value,
        String displayName,
        int sortOrder,
        UUID attributeValueId,
        UUID attributeId,
        AttributeDataType attributeDataType,
        String attributeValueDisplayName,
        String textValue,
        BigDecimal numericValue,
        Boolean booleanValue,
        LocalDate dateValue
) {
}
