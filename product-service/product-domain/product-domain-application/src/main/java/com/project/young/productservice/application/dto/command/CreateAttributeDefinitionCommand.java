package com.project.young.productservice.application.dto.command;

import com.project.young.productservice.domain.valueobject.AttributeDataType;

public record CreateAttributeDefinitionCommand(String code, String displayName,
                                               AttributeDataType dataType, String canonicalUnit) {
}
