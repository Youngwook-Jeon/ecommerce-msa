package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.productservice.application.dto.command.AddOptionValueCommand;
import com.project.young.productservice.application.port.output.AttributeCatalogPort;
import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.exception.AttributeDomainException;
import com.project.young.productservice.domain.exception.AttributeInactiveException;
import com.project.young.productservice.domain.exception.AttributeNotFoundException;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptionAttributeValidatorTest {
    @Mock
    AttributeCatalogPort catalog;
    @InjectMocks
    OptionAttributeValidator validator;

    @Test
    void requiresAttributeForNewGroup() {
        assertThatThrownBy(() -> validator.validateDefinition(null)).isInstanceOf(AttributeDomainException.class);
        verifyNoInteractions(catalog);
    }

    @Test
    void rejectsMissingDefinition() {
        UUID id = UUID.randomUUID();
        when(catalog.findDefinitionForUpdate(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> validator.validateDefinition(id)).isInstanceOf(AttributeNotFoundException.class);
    }

    @Test
    void rejectsInactiveDefinitionForNewGroup() {
        UUID id = UUID.randomUUID();
        when(catalog.findDefinitionForUpdate(id)).thenReturn(Optional.of(definition(id).deactivate()));
        assertThatThrownBy(() -> validator.validateDefinition(id)).isInstanceOf(AttributeInactiveException.class);
    }

    @Test
    void rejectsAddingValuesToLegacyGroup() {
        var group = OptionGroup.builder().name("LEGACY").displayName("Legacy").build();
        assertThatThrownBy(() -> validator.validateValues(group, List.of())).isInstanceOf(AttributeDomainException.class);
        verifyNoInteractions(catalog);
    }

    @Test
    void rejectsValueFromAnotherAttribute() {
        UUID id = UUID.randomUUID();
        UUID valueId = UUID.randomUUID();
        var group = OptionGroup.builder().name("RAM").displayName("RAM")
                .attributeId(new AttributeDefinitionId(id)).build();
        when(catalog.findDefinitionForUpdate(id)).thenReturn(Optional.of(definition(id)));
        when(catalog.findValueIds(id, Set.of(valueId))).thenReturn(Set.of());

        assertThatThrownBy(() -> validator.validateValues(group,
                List.of(AddOptionValueCommand.builder().attributeValueId(valueId).build())))
                .isInstanceOf(AttributeNotFoundException.class);
    }

    @Test
    void rejectsNewValuesAfterDeactivation() {
        UUID id = UUID.randomUUID();
        var group = OptionGroup.builder().name("RAM").displayName("RAM")
                .attributeId(new AttributeDefinitionId(id)).build();
        when(catalog.findDefinitionForUpdate(id)).thenReturn(Optional.of(definition(id).deactivate()));
        assertThatThrownBy(() -> validator.validateValues(group,
                List.of(AddOptionValueCommand.builder()
                .attributeValueId(UUID.randomUUID()).build())))
                .isInstanceOf(AttributeInactiveException.class);
    }

    @Test
    void validatesMultipleValuesWithOneBatchLookup() {
        UUID id = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var group = OptionGroup.builder().name("RAM").displayName("RAM")
                .attributeId(new AttributeDefinitionId(id)).build();
        when(catalog.findDefinitionForUpdate(id)).thenReturn(Optional.of(definition(id)));
        when(catalog.findValueIds(id, Set.of(first, second))).thenReturn(Set.of(first, second));

        validator.validateValues(group, List.of(command(first), command(second), command(first)));

        verify(catalog).findDefinitionForUpdate(id);
        verify(catalog).findValueIds(id, Set.of(first, second));
        verifyNoMoreInteractions(catalog);
    }

    @Test
    void rejectsBatchWhenAnyValueIsMissingOrBelongsToAnotherAttribute() {
        UUID id = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        var group = OptionGroup.builder().name("RAM").displayName("RAM")
                .attributeId(new AttributeDefinitionId(id)).build();
        when(catalog.findDefinitionForUpdate(id)).thenReturn(Optional.of(definition(id)));
        when(catalog.findValueIds(id, Set.of(first, missing))).thenReturn(Set.of(first));

        assertThatThrownBy(() -> validator.validateValues(group, List.of(command(first), command(missing))))
                .isInstanceOf(AttributeNotFoundException.class);
    }

    @Test
    void rejectsMissingValueIdBeforeBatchLookup() {
        UUID id = UUID.randomUUID();
        var group = OptionGroup.builder().name("RAM").displayName("RAM")
                .attributeId(new AttributeDefinitionId(id)).build();
        when(catalog.findDefinitionForUpdate(id)).thenReturn(Optional.of(definition(id)));

        assertThatThrownBy(() -> validator.validateValues(group,
                List.of(command(UUID.randomUUID()), AddOptionValueCommand.builder().build())))
                .isInstanceOf(AttributeDomainException.class);
        verify(catalog).findDefinitionForUpdate(id);
        verifyNoMoreInteractions(catalog);
    }

    private AddOptionValueCommand command(UUID valueId) {
        return AddOptionValueCommand.builder().attributeValueId(valueId).build();
    }

    private AttributeDefinition definition(UUID id) {
        return new AttributeDefinition(new AttributeDefinitionId(id), "test_ram", "RAM", AttributeDataType.NUMBER, "GB");
    }
}
