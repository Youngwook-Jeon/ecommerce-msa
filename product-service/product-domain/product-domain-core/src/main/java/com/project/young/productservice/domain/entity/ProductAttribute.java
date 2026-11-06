package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.BaseEntity;
import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.common.domain.valueobject.AttributeValueId;
import com.project.young.common.domain.valueobject.ProductAttributeId;
import com.project.young.productservice.domain.exception.ProductDomainException;
import lombok.Getter;

/** A single fixed specification; category policy is validated separately from this fact. */
@Getter
public class ProductAttribute extends BaseEntity<ProductAttributeId> {
    private final AttributeDefinitionId attributeId;
    private final AttributeValueId attributeValueId;

    public ProductAttribute(ProductAttributeId id, AttributeDefinitionId attributeId, AttributeValueId attributeValueId) {
        if (id == null || id.getValue() == null || attributeId == null || attributeId.getValue() == null
                || attributeValueId == null || attributeValueId.getValue() == null) {
            throw new ProductDomainException("Product attribute ID, definition ID, and value ID are required");
        }
        super.setId(id);
        this.attributeId = attributeId;
        this.attributeValueId = attributeValueId;
    }

    public ProductAttribute changeValue(AttributeValueId valueId) {
        return new ProductAttribute(getId(), attributeId, valueId);
    }
}
