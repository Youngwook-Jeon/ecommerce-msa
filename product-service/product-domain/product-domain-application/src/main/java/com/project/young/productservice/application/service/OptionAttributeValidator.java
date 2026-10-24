package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.command.AddOptionValueCommand;
import com.project.young.productservice.application.port.output.AttributeCatalogPort;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.exception.AttributeDomainException;
import com.project.young.productservice.domain.exception.AttributeNotFoundException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Validates dictionary bindings within the caller's write transaction. */
@Component
public class OptionAttributeValidator {
    private final AttributeCatalogPort catalog;

    public OptionAttributeValidator(AttributeCatalogPort catalog) {
        this.catalog = catalog;
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

    public void validateValues(OptionGroup group, List<AddOptionValueCommand> commands) {
        if (group.getAttributeId() == null) {
            throw new AttributeDomainException("Legacy option groups must be explicitly mapped before adding values");
        }
        UUID attributeId = group.getAttributeId().getValue();
        validateDefinition(attributeId);
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
    }
}
