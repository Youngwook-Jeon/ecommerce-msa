package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.application.dto.command.AddOptionValueCommand;
import com.project.young.productservice.application.port.output.AttributeCatalogPort;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.exception.AttributeDomainException;
import com.project.young.productservice.domain.exception.AttributeNotFoundException;
import com.project.young.productservice.domain.exception.OptionDomainException;
import com.project.young.productservice.domain.exception.OptionGroupNotFoundException;
import com.project.young.productservice.domain.repository.OptionGroupRepository;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Validates dictionary bindings within the caller's write transaction. */
@Component
public class OptionAttributeValidator {
    private final AttributeCatalogPort catalog;
    private final OptionGroupRepository optionGroups;

    public OptionAttributeValidator(AttributeCatalogPort catalog, OptionGroupRepository optionGroups) {
        this.catalog = catalog;
        this.optionGroups = optionGroups;
    }

    public void validateDefinition(UUID attributeId) {
        if (attributeId == null) {
            throw new AttributeDomainException("Attribute ID is required for a new option group");
        }
        // Serialize new dictionary usage with definition deactivation and deletion.
        catalog.findDefinitionForUpdate(attributeId)
                .orElseThrow(() -> new AttributeNotFoundException("Attribute definition not found"))
                .requireActive();
    }

    public void validateActivation(OptionGroupId groupId) {
        var binding = optionGroups.findAttributeBinding(groupId)
                .orElseThrow(() -> new OptionGroupNotFoundException("Option group not found."));
        if (binding.attributeId() == null) {
            throw new AttributeDomainException("Legacy option groups must be explicitly mapped before activation");
        }
        // Bindings are immutable. Lock the definition before the optimistic group UPDATE
        // so activation is serialized with definition deactivation and deletion.
        validateDefinition(binding.attributeId().getValue());
    }

    public OptionGroup lockGroupAndValidateValues(OptionGroupId groupId, List<AddOptionValueCommand> commands) {
        var binding = optionGroups.findAttributeBinding(groupId)
                .orElseThrow(() -> new OptionGroupNotFoundException("Option group not found."));
        if (binding.attributeId() == null) {
            throw new AttributeDomainException("Legacy option groups must be explicitly mapped before adding values");
        }
        UUID attributeId = binding.attributeId().getValue();
        // Lock order: definition -> group. The initial scalar lookup acquires no row lock.
        validateDefinition(attributeId);
        var group = optionGroups.findByIdForUpdate(groupId)
                .orElseThrow(() -> new OptionGroupNotFoundException("Option group not found."));
        if (!binding.attributeId().equals(group.getAttributeId())) {
            throw new AttributeDomainException("Option group attribute binding changed; reload before adding values");
        }
        if (group.isDeleted()) {
            throw new OptionDomainException("Cannot modify an option group that has been deleted.");
        }
        Set<UUID> valueIds = new HashSet<>();
        for (AddOptionValueCommand command : commands) {
            if (command == null || command.getAttributeValueId() == null) {
                throw new AttributeDomainException("Attribute value ID is required for a new option value");
            }
            valueIds.add(command.getAttributeValueId());
        }
        if (!catalog.findValueIds(attributeId, valueIds).containsAll(valueIds)) {
            throw new AttributeNotFoundException("Attribute value not found for the option group's attribute");
        }
        return group;
    }
}
