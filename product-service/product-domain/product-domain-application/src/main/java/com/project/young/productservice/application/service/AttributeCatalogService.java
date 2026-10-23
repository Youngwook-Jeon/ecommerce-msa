package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.AttributeValueId;
import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.productservice.application.dto.command.CreateAttributeDefinitionCommand;
import com.project.young.productservice.application.dto.command.CreateAttributeValueCommand;
import com.project.young.productservice.application.dto.command.SetCategoryAttributeCommand;
import com.project.young.productservice.application.dto.result.CategoryAttributeResult;
import com.project.young.productservice.application.port.output.AttributeCatalogPort;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.application.port.output.view.AttributeCategoryView;
import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.entity.AttributeValue;
import com.project.young.productservice.domain.exception.AttributeDomainException;
import com.project.young.productservice.domain.exception.AttributeNotFoundException;
import com.project.young.productservice.domain.exception.AttributeInUseException;
import com.project.young.productservice.domain.valueobject.CategoryAttributePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class AttributeCatalogService {
    private final AttributeCatalogPort catalog;
    private final IdGenerator idGenerator;

    public AttributeCatalogService(AttributeCatalogPort catalog, IdGenerator idGenerator) {
        this.catalog = catalog;
        this.idGenerator = idGenerator;
    }

    @Transactional(readOnly = true)
    public List<AttributeDefinition> definitions() {
        return catalog.findDefinitions();
    }

    @Transactional(readOnly = true)
    public AttributeDefinition definition(UUID id) {
        return catalog.findDefinition(id).orElseThrow(() -> new AttributeNotFoundException("Attribute definition not found"));
    }

    public AttributeDefinition createDefinition(CreateAttributeDefinitionCommand command) {
        var definition = new AttributeDefinition(new AttributeDefinitionId(idGenerator.generateId()),
                command.code(), command.displayName(), command.dataType(), command.canonicalUnit());
        catalog.insertDefinition(definition);
        log.info("Attribute definition created: attributeId={}, code={}", definition.getId().getValue(), definition.getCode());
        return definition;
    }

    public AttributeDefinition renameDefinition(UUID id, String displayName) {
        var renamed = definitionForUpdate(id).rename(displayName);
        catalog.updateDefinition(renamed);
        log.info("Attribute definition renamed: attributeId={}", id);
        return renamed;
    }

    public AttributeDefinition deactivateDefinition(UUID id) {
        var inactive = definitionForUpdate(id).deactivate();
        catalog.updateDefinition(inactive);
        log.info("Attribute definition deactivated: attributeId={}", id);
        return inactive;
    }

    public void deleteDefinition(UUID id) {
        definitionForUpdate(id);
        if (catalog.hasDefinitionReferences(id)) {
            throw new AttributeInUseException();
        }
        catalog.deleteDefinition(id);
        log.info("Attribute definition deleted: attributeId={}", id);
    }

    @Transactional(readOnly = true)
    public List<AttributeValue> values(UUID attributeId) {
        definition(attributeId);
        return catalog.findValues(attributeId);
    }

    public AttributeValue createValue(UUID attributeId, CreateAttributeValueCommand command) {
        var definition = definitionForUpdate(attributeId);
        definition.requireActive();
        var value = new AttributeValue(new AttributeValueId(idGenerator.generateId()), definition.getId(),
                definition.getDataType(), command.displayName(), command.textValue(), command.numericValue(),
                command.booleanValue(), command.dateValue());
        catalog.insertValue(value);
        log.info("Attribute value created: attributeId={}, valueId={}", attributeId, value.getId().getValue());
        return value;
    }

    public AttributeValue renameValue(UUID attributeId, UUID valueId, String displayName) {
        var value = catalog.findValue(attributeId, valueId)
                .orElseThrow(() -> new AttributeNotFoundException("Attribute value not found"));
        var renamed = value.rename(displayName);
        catalog.updateValue(renamed);
        log.info("Attribute value renamed: attributeId={}, valueId={}", attributeId, valueId);
        return renamed;
    }

    public void deleteValue(UUID attributeId, UUID valueId) {
        catalog.findValue(attributeId, valueId)
                .orElseThrow(() -> new AttributeNotFoundException("Attribute value not found"));
        catalog.deleteValue(valueId);
        log.info("Attribute value deleted: attributeId={}, valueId={}", attributeId, valueId);
    }

    public CategoryAttributePolicy setPolicy(Long categoryId, UUID attributeId, SetCategoryAttributeCommand command) {
        category(categoryId);
        var definition = definitionForUpdate(attributeId);
        if (command.enabled()) {
            definition.requireActive();
        }
        var policy = new CategoryAttributePolicy(categoryId, attributeId, command.enabled(), command.required(),
                command.optionAllowed(), command.filterable(), command.displayOrder());
        var saved = catalog.savePolicy(policy);
        log.info("Category attribute policy saved: categoryId={}, attributeId={}, enabled={}",
                categoryId, attributeId, saved.enabled());
        return saved;
    }

    public void removePolicy(Long categoryId, UUID attributeId) {
        category(categoryId);
        definition(attributeId);
        catalog.deletePolicy(categoryId, attributeId);
        log.info("Category attribute policy removed: categoryId={}, attributeId={}", categoryId, attributeId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<CategoryAttributeResult> categoryPolicies(Long categoryId) {
        category(categoryId);
        return results(categoryId, catalog.findPoliciesByCategoryId(categoryId));
    }

    private AttributeDefinition definitionForUpdate(UUID id) {
        return catalog.findDefinitionForUpdate(id)
                .orElseThrow(() -> new AttributeNotFoundException("Attribute definition not found"));
    }

    private AttributeCategoryView category(Long id) {
        if (id == null || id < 1) {
            throw new AttributeDomainException("Category ID must be positive");
        }
        return catalog.findCategory(id).filter(category -> !category.deleted())
                .orElseThrow(() -> new AttributeNotFoundException("Category not found"));
    }

    private List<CategoryAttributeResult> results(Long categoryId, List<CategoryAttributePolicy> policies) {
        if (policies.isEmpty()) {
            return List.of();
        }
        var attributeIds = policies.stream().map(CategoryAttributePolicy::attributeId).collect(Collectors.toSet());
        Map<UUID, AttributeDefinition> definitions = catalog.findDefinitionsByIds(attributeIds).stream()
                .collect(Collectors.toMap(definition -> definition.getId().getValue(), Function.identity()));
        return policies.stream().map(policy -> {
                    requirePolicyCategory(categoryId, policy.categoryId());
                    return new CategoryAttributeResult(requireDefinition(definitions, policy.attributeId()), policy);
                })
                .sorted(Comparator.comparingInt((CategoryAttributeResult result) -> result.policy().displayOrder())
                        .thenComparing(result -> result.attribute().getCode()))
                .toList();
    }

    private AttributeDefinition requireDefinition(Map<UUID, AttributeDefinition> definitions, UUID attributeId) {
        AttributeDefinition definition = definitions.get(attributeId);
        if (definition == null) {
            throw new IllegalStateException(
                    "Attribute definition missing for category policy: attributeId=" + attributeId);
        }
        return definition;
    }

    private void requirePolicyCategory(Long categoryId, Long policyCategoryId) {
        if (!categoryId.equals(policyCategoryId)) {
            throw new IllegalStateException("Category policy does not belong to requested category: categoryId="
                    + categoryId + ", policyCategoryId=" + policyCategoryId);
        }
    }
}
