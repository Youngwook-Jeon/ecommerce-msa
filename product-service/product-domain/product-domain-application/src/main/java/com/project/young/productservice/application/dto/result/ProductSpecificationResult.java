package com.project.young.productservice.application.dto.result;

import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.entity.AttributeValue;

public record ProductSpecificationResult(AttributeDefinition attribute, AttributeValue value) {
}
