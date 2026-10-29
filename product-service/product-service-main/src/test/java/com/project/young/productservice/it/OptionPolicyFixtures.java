package com.project.young.productservice.it;

import com.project.young.productservice.dataaccess.entity.AttributeDefinitionEntity;
import com.project.young.productservice.dataaccess.entity.AttributeValueEntity;
import com.project.young.productservice.dataaccess.entity.CategoryAttributeEntity;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import com.project.young.productservice.domain.valueobject.AttributeStatus;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

final class OptionPolicyFixtures {
    private OptionPolicyFixtures() {
    }

    static void link(EntityManager entityManager, OptionGroupEntity group, List<Long> categoryIds) {
        UUID attributeId = UUID.randomUUID();
        var definition = new AttributeDefinitionEntity();
        definition.setId(attributeId);
        definition.setCode("test_option_" + attributeId.toString().replace('-', '_'));
        definition.setDisplayName("Test option attribute");
        definition.setDataType(AttributeDataType.ENUM);
        definition.setStatus(AttributeStatus.ACTIVE);
        entityManager.persist(definition);
        group.setAttributeId(attributeId);
        for (var option : group.getOptionValues()) {
            var value = new AttributeValueEntity();
            value.setId(UUID.randomUUID());
            value.setAttributeId(attributeId);
            value.setDataType(AttributeDataType.ENUM);
            value.setDisplayName(option.getDisplayName());
            value.setTextValue(option.getValue());
            entityManager.persist(value);
            option.setAttributeId(attributeId);
            option.setAttributeValueId(value.getId());
        }
        for (Long categoryId : categoryIds) {
            var policy = new CategoryAttributeEntity();
            policy.setId(UUID.randomUUID());
            policy.setCategoryId(categoryId);
            policy.setAttributeId(attributeId);
            policy.setEnabled(true);
            policy.setOptionAllowed(true);
            entityManager.persist(policy);
        }
    }
}
