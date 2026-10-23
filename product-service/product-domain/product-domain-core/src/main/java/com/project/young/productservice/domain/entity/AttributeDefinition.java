package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.productservice.domain.exception.AttributeDomainException;
import com.project.young.productservice.domain.exception.AttributeInactiveException;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import com.project.young.productservice.domain.valueobject.AttributeStatus;

import lombok.Getter;

@Getter
public class AttributeDefinition extends AggregateRoot<AttributeDefinitionId> {
    private final String code;
    private final String displayName;
    private final AttributeDataType dataType;
    private final String canonicalUnit;
    private final AttributeStatus status;

    public AttributeDefinition(AttributeDefinitionId id, String code, String displayName,
                               AttributeDataType dataType, String canonicalUnit) {
        this(id, code, displayName, dataType, canonicalUnit, AttributeStatus.ACTIVE);
    }

    public AttributeDefinition(AttributeDefinitionId id, String code, String displayName,
                               AttributeDataType dataType, String canonicalUnit, AttributeStatus status) {
        validateRequiredFields(id, dataType, status);
        validateCode(code);
        super.setId(id);
        this.code = code;
        this.displayName = validateDisplayName(displayName);
        this.dataType = dataType;
        this.canonicalUnit = normalizeCanonicalUnit(dataType, canonicalUnit);
        this.status = status;
    }

    public AttributeDefinition rename(String name) {
        String validatedName = validateDisplayName(name);
        return new AttributeDefinition(getId(), code, validatedName, dataType, canonicalUnit, status);
    }

    public AttributeDefinition deactivate() {
        return new AttributeDefinition(getId(), code, displayName, dataType, canonicalUnit, AttributeStatus.INACTIVE);
    }

    public boolean isActive() {
        return status == AttributeStatus.ACTIVE;
    }

    public void requireActive() {
        if (!isActive()) {
            throw new AttributeInactiveException();
        }
    }

    private static void validateRequiredFields(AttributeDefinitionId id, AttributeDataType dataType,
                                               AttributeStatus status) {
        if (id == null) {
            throw new AttributeDomainException("Attribute ID is required");
        }
        if (dataType == null) {
            throw new AttributeDomainException("Attribute data type is required");
        }
        if (status == null) {
            throw new AttributeDomainException("Attribute status is required");
        }
    }

    private static void validateCode(String code) {
        if (code == null || !code.matches("[a-z][a-z0-9_]{0,99}")) {
            throw new AttributeDomainException("Attribute code must be a lowercase identifier of at most 100 characters");
        }
    }

    static String validateDisplayName(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 100) {
            throw new AttributeDomainException("Display name must contain between 1 and 100 characters");
        }
        return name.strip();
    }

    private static String normalizeCanonicalUnit(AttributeDataType dataType, String unit) {
        if (unit == null) {
            return null;
        }
        String normalized = unit.strip();
        if (dataType != AttributeDataType.NUMBER || normalized.isEmpty() || normalized.length() > 20) {
            throw new AttributeDomainException("Only numeric attributes may have a nonblank unit of at most 20 characters");
        }
        return normalized;
    }
}
