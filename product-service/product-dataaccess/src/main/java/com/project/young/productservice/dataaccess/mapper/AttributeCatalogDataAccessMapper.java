package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.common.domain.valueobject.AttributeValueId;
import com.project.young.productservice.dataaccess.entity.AttributeDefinitionEntity;
import com.project.young.productservice.dataaccess.entity.AttributeValueEntity;
import com.project.young.productservice.dataaccess.entity.CategoryAttributeEntity;
import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.entity.AttributeValue;
import com.project.young.productservice.domain.valueobject.CategoryAttributePolicy;
import org.springframework.stereotype.Component;

@Component
public class AttributeCatalogDataAccessMapper {
    public AttributeDefinition toDomain(AttributeDefinitionEntity entity) {
        return new AttributeDefinition(new AttributeDefinitionId(entity.getId()), entity.getCode(), entity.getDisplayName(),
                entity.getDataType(), entity.getCanonicalUnit(), entity.getStatus());
    }

    public AttributeDefinitionEntity toEntity(AttributeDefinition definition) {
        var entity = new AttributeDefinitionEntity();
        entity.setId(definition.getId().getValue());
        entity.setCode(definition.getCode());
        entity.setDisplayName(definition.getDisplayName());
        entity.setDataType(definition.getDataType());
        entity.setCanonicalUnit(definition.getCanonicalUnit());
        entity.setStatus(definition.getStatus());
        return entity;
    }

    public AttributeValue toDomain(AttributeValueEntity entity) {
        return new AttributeValue(new AttributeValueId(entity.getId()), new AttributeDefinitionId(entity.getAttributeId()),
                entity.getDataType(), entity.getDisplayName(), entity.getTextValue(), entity.getNumericValue(),
                entity.getBooleanValue(), entity.getDateValue());
    }

    public AttributeValueEntity toEntity(AttributeValue value) {
        var entity = new AttributeValueEntity();
        entity.setId(value.getId().getValue());
        entity.setAttributeId(value.getAttributeId().getValue());
        entity.setDataType(value.getDataType());
        entity.setDisplayName(value.getDisplayName());
        entity.setTextValue(value.getTextValue());
        entity.setNumericValue(value.getNumericValue());
        entity.setBooleanValue(value.getBooleanValue());
        entity.setDateValue(value.getDateValue());
        return entity;
    }

    public CategoryAttributePolicy toDomain(CategoryAttributeEntity entity) {
        return new CategoryAttributePolicy(entity.getCategoryId(), entity.getAttributeId(), entity.isEnabled(),
                entity.isRequired(), entity.isOptionAllowed(), entity.isFilterable(), entity.getDisplayOrder());
    }

    public void apply(CategoryAttributePolicy policy, CategoryAttributeEntity entity) {
        entity.setCategoryId(policy.categoryId());
        entity.setAttributeId(policy.attributeId());
        entity.setEnabled(policy.enabled());
        entity.setRequired(policy.required());
        entity.setOptionAllowed(policy.optionAllowed());
        entity.setFilterable(policy.filterable());
        entity.setDisplayOrder(policy.displayOrder());
    }
}
