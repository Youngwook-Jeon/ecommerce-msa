package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.application.dto.command.AddProductOptionValueCommand;
import com.project.young.productservice.application.port.output.AttributeCatalogPort;
import com.project.young.productservice.domain.entity.OptionValue;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.repository.OptionGroupRepository;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Validates new product option assignments within the product write transaction.
 */
@Component
public class ProductOptionPolicyValidator {
    private final AttributeCatalogPort catalog;
    private final OptionGroupRepository optionGroups;

    public ProductOptionPolicyValidator(AttributeCatalogPort catalog, OptionGroupRepository optionGroups) {
        this.catalog = catalog;
        this.optionGroups = optionGroups;
    }

    public void validate(Product product, OptionGroupId groupId, List<AddProductOptionValueCommand> commands) {
        Set<UUID> optionValueIds = requestedValueIds(commands);
        if (groupId == null || groupId.getValue() == null) {
            throw new ProductDomainException("Global option group ID is required");
        }
        Long categoryId = product.getCategoryId()
                .orElseThrow(() -> new ProductDomainException("A category is required before adding product options"))
                .getValue();
        catalog.findCategory(categoryId).filter(category -> !category.deleted())
                .orElseThrow(() -> new ProductDomainException("Product category is missing or deleted"));

        var binding = optionGroups.findAttributeBinding(groupId)
                .orElseThrow(() -> new ProductDomainException("Global option group not found"));
        if (binding.attributeId() == null) {
            throw new ProductDomainException("Global option group must be linked to an attribute definition");
        }
        UUID attributeId = binding.attributeId().getValue();
        // Lock order: definition -> group. Binding discovery reads scalar IDs without a row lock.
        var definition = catalog.findDefinitionForUpdate(attributeId)
                .orElseThrow(() -> new ProductDomainException("Option attribute definition not found"));
        if (!definition.isActive()) {
            throw new ProductDomainException("Inactive attribute definitions cannot be used for new product options");
        }
        var group = optionGroups.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ProductDomainException("Global option group not found"));
        if (!binding.attributeId().equals(group.getAttributeId())) {
            throw new ProductDomainException("Option group attribute binding changed; reload before adding options");
        }
        if (group.getStatus() != OptionStatus.ACTIVE) {
            throw new ProductDomainException("Global option group is not allowed for product composition: " + groupId.getValue());
        }
        catalog.findPolicy(categoryId, attributeId).filter(policy -> policy.enabled() && policy.optionAllowed())
                .orElseThrow(() -> new ProductDomainException("This category does not allow the option attribute"));

        Map<UUID, OptionValue> values = group.getOptionValues().stream()
                .collect(Collectors.toMap(value -> value.getId().getValue(), Function.identity()));
        Set<UUID> canonicalIds = new HashSet<>();
        for (UUID valueId : optionValueIds) {
            var value = values.get(valueId);
            if (value == null) {
                throw new ProductDomainException("Option value " + valueId + " does not belong to option group " + groupId.getValue());
            }
            if (value.getStatus() != OptionStatus.ACTIVE) {
                throw new ProductDomainException("Global option value is not allowed for product composition: " + valueId);
            }
            if (value.getAttributeValueId() == null) {
                throw new ProductDomainException("Selected option values must be linked to canonical attribute values");
            }
            if (!canonicalIds.add(value.getAttributeValueId().getValue())) {
                throw new ProductDomainException("Selected option values must reference distinct canonical attribute values");
            }
        }
        if (!catalog.findValueIds(attributeId, canonicalIds).containsAll(canonicalIds)) {
            throw new ProductDomainException("Selected canonical values do not belong to the option attribute");
        }
    }

    private Set<UUID> requestedValueIds(List<AddProductOptionValueCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("Product option values must not be empty.");
        }
        Set<UUID> ids = new HashSet<>();
        for (var command : commands) {
            if (command == null || command.getOptionValueId() == null) {
                throw new ProductDomainException("Product option value IDs must not be null");
            }
            if (!ids.add(command.getOptionValueId())) {
                throw new ProductDomainException("Product option value IDs must not be duplicated");
            }
        }
        return ids;
    }
}
