package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.dto.result.CategoryAttributeResult;
import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.entity.AttributeValue;
import com.project.young.productservice.web.dto.AttributeCatalogResponses;
import org.springframework.stereotype.Component;

@Component
public class AttributeCatalogResponseMapper {
    public AttributeCatalogResponses.Definition definition(AttributeDefinition definition) {
        return new AttributeCatalogResponses.Definition(definition.getId().getValue(), definition.getCode(), definition.getDisplayName(),
                definition.getDataType().name(), definition.getCanonicalUnit(), definition.getStatus().name());
    }

    public AttributeCatalogResponses.Value value(AttributeValue value) {
        return new AttributeCatalogResponses.Value(value.getId().getValue(), value.getAttributeId().getValue(), value.getDataType().name(),
                value.getDisplayName(), value.getTextValue(), value.getNumericValue(), value.getBooleanValue(), value.getDateValue());
    }

    public AttributeCatalogResponses.CategoryAttribute policy(CategoryAttributeResult result) {
        var policy = result.policy();
        return new AttributeCatalogResponses.CategoryAttribute(definition(result.attribute()),
                new AttributeCatalogResponses.Policy(policy.attributeId(), policy.enabled(), policy.required(),
                        policy.optionAllowed(), policy.filterable(), policy.displayOrder(), result.usable(),
                        result.optionAvailable(), result.filterAvailable()));
    }
}
