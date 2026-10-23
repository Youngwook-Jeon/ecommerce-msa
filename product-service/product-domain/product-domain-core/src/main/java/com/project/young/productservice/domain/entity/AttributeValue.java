package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.BaseEntity;
import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.common.domain.valueobject.AttributeValueId;
import com.project.young.productservice.domain.exception.AttributeDomainException;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
public class AttributeValue extends BaseEntity<AttributeValueId> {
    private final AttributeDefinitionId attributeId;
    private final AttributeDataType dataType;
    private final String displayName;
    private final String textValue;
    private final BigDecimal numericValue;
    private final Boolean booleanValue;
    private final LocalDate dateValue;

    public AttributeValue(AttributeValueId id, AttributeDefinitionId attributeId, AttributeDataType dataType,
                          String displayName, String textValue, BigDecimal numericValue,
                          Boolean booleanValue, LocalDate dateValue) {
        validateRequiredFields(id, attributeId, dataType);
        validateTypedValue(dataType, textValue, numericValue, booleanValue, dateValue);
        validateDateValue(dateValue);
        super.setId(id);
        this.attributeId = attributeId;
        this.dataType = dataType;
        this.displayName = AttributeDefinition.validateDisplayName(displayName);
        this.textValue = normalizeTextValue(textValue);
        this.numericValue = normalizeNumericValue(numericValue);
        this.booleanValue = booleanValue;
        this.dateValue = dateValue;
    }

    public AttributeValue rename(String name) {
        String validatedName = AttributeDefinition.validateDisplayName(name);
        return new AttributeValue(getId(), attributeId, dataType, validatedName,
                textValue, numericValue, booleanValue, dateValue);
    }

    private static void validateRequiredFields(AttributeValueId id, AttributeDefinitionId attributeId,
                                               AttributeDataType dataType) {
        if (id == null) {
            throw new AttributeDomainException("Value ID is required");
        }
        if (attributeId == null) {
            throw new AttributeDomainException("Attribute ID is required");
        }
        if (dataType == null) {
            throw new AttributeDomainException("Attribute data type is required");
        }
    }

    private static void validateTypedValue(AttributeDataType dataType, String textValue, BigDecimal numericValue,
                                           Boolean booleanValue, LocalDate dateValue) {
        int count = (textValue == null ? 0 : 1) + (numericValue == null ? 0 : 1)
                + (booleanValue == null ? 0 : 1) + (dateValue == null ? 0 : 1);
        boolean matches = switch (dataType) {
            case TEXT, ENUM -> textValue != null;
            case NUMBER -> numericValue != null;
            case BOOLEAN -> booleanValue != null;
            case DATE -> dateValue != null;
        };
        if (count != 1 || !matches) {
            throw new AttributeDomainException("Exactly one value matching the attribute data type is required");
        }
    }

    private static String normalizeTextValue(String textValue) {
        if (textValue == null) {
            return null;
        }
        String normalized = textValue.strip();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new AttributeDomainException("Text value must contain between 1 and 100 characters");
        }
        return normalized;
    }

    private static BigDecimal normalizeNumericValue(BigDecimal numericValue) {
        if (numericValue == null) {
            return null;
        }
        if (numericValue.signum() != 0 && (long) numericValue.precision() - numericValue.scale() > 13) {
            throw new AttributeDomainException("Numeric value must fit 13 integer digits and 6 decimal places");
        }
        BigDecimal normalized = numericValue.stripTrailingZeros();
        if (normalized.scale() > 6) {
            throw new AttributeDomainException("Numeric value must fit 13 integer digits and 6 decimal places");
        }
        return normalized;
    }

    private static void validateDateValue(LocalDate dateValue) {
        if (dateValue != null && (dateValue.getYear() < 1 || dateValue.getYear() > 9999)) {
            throw new AttributeDomainException("Date value must have a year between 1 and 9999");
        }
    }
}
