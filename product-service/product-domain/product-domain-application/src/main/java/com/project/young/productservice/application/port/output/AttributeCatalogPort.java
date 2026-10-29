package com.project.young.productservice.application.port.output;

import com.project.young.productservice.application.port.output.view.AttributeCategoryView;
import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.entity.AttributeValue;
import com.project.young.productservice.domain.valueobject.CategoryAttributePolicy;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AttributeCatalogPort {
    List<AttributeDefinition> findDefinitions();

    List<AttributeDefinition> findDefinitionsByIds(Set<UUID> ids);

    Optional<AttributeDefinition> findDefinition(UUID id);

    /** Acquire required definition locks before group locks, sorting IDs when locking multiple definitions. */
    Optional<AttributeDefinition> findDefinitionForUpdate(UUID id);

    boolean hasDefinitionReferences(UUID id);

    void insertDefinition(AttributeDefinition definition);

    void updateDefinition(AttributeDefinition definition);

    void deleteDefinition(UUID id);

    List<AttributeValue> findValues(UUID attributeId);

    Optional<AttributeValue> findValue(UUID attributeId, UUID valueId);

    Set<UUID> findValueIds(UUID attributeId, Set<UUID> valueIds);

    void insertValue(AttributeValue value);

    void updateValue(AttributeValue value);

    void deleteValue(UUID valueId);

    Optional<AttributeCategoryView> findCategory(Long categoryId);

    List<CategoryAttributePolicy> findPoliciesByCategoryId(Long categoryId);

    Optional<CategoryAttributePolicy> findPolicy(Long categoryId, UUID attributeId);

    CategoryAttributePolicy savePolicy(CategoryAttributePolicy policy);

    void deletePolicy(Long categoryId, UUID attributeId);
}
