package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.application.dto.command.AddOptionValueCommand;
import com.project.young.productservice.application.port.output.AttributeCatalogPort;
import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.exception.AttributeDomainException;
import com.project.young.productservice.domain.exception.AttributeInactiveException;
import com.project.young.productservice.domain.exception.AttributeNotFoundException;
import com.project.young.productservice.domain.repository.OptionGroupRepository;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import com.project.young.productservice.domain.valueobject.OptionGroupAttributeBinding;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class OptionAttributeValidatorTest {
    @Mock
    AttributeCatalogPort catalog;
    @Mock
    OptionGroupRepository optionGroups;
    final OptionGroupId groupId = new OptionGroupId(UUID.randomUUID());
    @InjectMocks
    OptionAttributeValidator validator;

    @Test
    void activation_locks_the_linked_definition_without_locking_the_group() {
        UUID id = UUID.randomUUID();
        when(optionGroups.findAttributeBinding(groupId)).thenReturn(Optional.of(
                new OptionGroupAttributeBinding(groupId, new AttributeDefinitionId(id))));
        when(catalog.findDefinitionForUpdate(id)).thenReturn(Optional.of(definition(id)));

        validator.validateActivation(groupId);

        var ordered = inOrder(optionGroups, catalog);
        ordered.verify(optionGroups).findAttributeBinding(groupId);
        ordered.verify(catalog).findDefinitionForUpdate(id);
        verifyNoMoreInteractions(optionGroups, catalog);
    }

    @Test
    void activation_of_a_legacy_group_is_rejected() {
        when(optionGroups.findAttributeBinding(groupId))
                .thenReturn(Optional.of(new OptionGroupAttributeBinding(groupId, null)));

        assertThatThrownBy(() -> validator.validateActivation(groupId)).isInstanceOf(AttributeDomainException.class);
        verifyNoInteractions(catalog);
    }

    @Test
    void activation_with_an_inactive_definition_is_rejected() {
        UUID id = UUID.randomUUID();
        when(optionGroups.findAttributeBinding(groupId)).thenReturn(Optional.of(
                new OptionGroupAttributeBinding(groupId, new AttributeDefinitionId(id))));
        when(catalog.findDefinitionForUpdate(id)).thenReturn(Optional.of(definition(id).deactivate()));

        assertThatThrownBy(() -> validator.validateActivation(groupId)).isInstanceOf(AttributeInactiveException.class);
        verify(optionGroups, never()).findByIdForUpdate(any());
    }

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
        var group = OptionGroup.builder().id(groupId).name("LEGACY").displayName("Legacy").build();
        givenGroup(group);
        assertThatThrownBy(() -> validator.lockGroupAndValidateValues(groupId, List.of())).isInstanceOf(AttributeDomainException.class);
        verifyNoInteractions(catalog);
    }

    @Test
    void rejectsValueFromAnotherAttribute() {
        UUID id = UUID.randomUUID();
        UUID valueId = UUID.randomUUID();
        var group = OptionGroup.builder().id(groupId).name("RAM").displayName("RAM")
                .attributeId(new AttributeDefinitionId(id)).build();
        givenGroup(group);
        when(catalog.findDefinitionForUpdate(id)).thenReturn(Optional.of(definition(id)));
        when(catalog.findValueIds(id, Set.of(valueId))).thenReturn(Set.of());

        assertThatThrownBy(() -> validator.lockGroupAndValidateValues(groupId,
                List.of(AddOptionValueCommand.builder().attributeValueId(valueId).build())))
                .isInstanceOf(AttributeNotFoundException.class);
    }

    @Test
    void rejectsNewValuesAfterDeactivation() {
        UUID id = UUID.randomUUID();
        var group = OptionGroup.builder().id(groupId).name("RAM").displayName("RAM")
                .attributeId(new AttributeDefinitionId(id)).build();
        givenGroup(group);
        when(catalog.findDefinitionForUpdate(id)).thenReturn(Optional.of(definition(id).deactivate()));
        assertThatThrownBy(() -> validator.lockGroupAndValidateValues(groupId,
                List.of(AddOptionValueCommand.builder()
                .attributeValueId(UUID.randomUUID()).build())))
                .isInstanceOf(AttributeInactiveException.class);
    }

    @Test
    void validatesMultipleValuesWithOneBatchLookup() {
        UUID id = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var group = OptionGroup.builder().id(groupId).name("RAM").displayName("RAM")
                .attributeId(new AttributeDefinitionId(id)).build();
        givenGroup(group);
        when(catalog.findDefinitionForUpdate(id)).thenReturn(Optional.of(definition(id)));
        when(catalog.findValueIds(id, Set.of(first, second))).thenReturn(Set.of(first, second));

        validator.lockGroupAndValidateValues(groupId, List.of(command(first), command(second), command(first)));

        var ordered = inOrder(optionGroups, catalog);
        ordered.verify(optionGroups).findAttributeBinding(groupId);
        ordered.verify(catalog).findDefinitionForUpdate(id);
        ordered.verify(optionGroups).findByIdForUpdate(groupId);
        ordered.verify(catalog).findValueIds(id, Set.of(first, second));
        verifyNoMoreInteractions(catalog);
    }

    @Test
    void rejectsBatchWhenAnyValueIsMissingOrBelongsToAnotherAttribute() {
        UUID id = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        var group = OptionGroup.builder().id(groupId).name("RAM").displayName("RAM")
                .attributeId(new AttributeDefinitionId(id)).build();
        givenGroup(group);
        when(catalog.findDefinitionForUpdate(id)).thenReturn(Optional.of(definition(id)));
        when(catalog.findValueIds(id, Set.of(first, missing))).thenReturn(Set.of(first));

        assertThatThrownBy(() -> validator.lockGroupAndValidateValues(groupId, List.of(command(first), command(missing))))
                .isInstanceOf(AttributeNotFoundException.class);
    }

    @Test
    void rejectsMissingValueIdBeforeBatchLookup() {
        UUID id = UUID.randomUUID();
        var group = OptionGroup.builder().id(groupId).name("RAM").displayName("RAM")
                .attributeId(new AttributeDefinitionId(id)).build();
        givenGroup(group);
        when(catalog.findDefinitionForUpdate(id)).thenReturn(Optional.of(definition(id)));

        assertThatThrownBy(() -> validator.lockGroupAndValidateValues(groupId,
                List.of(command(UUID.randomUUID()), AddOptionValueCommand.builder().build())))
                .isInstanceOf(AttributeDomainException.class);
        verify(catalog).findDefinitionForUpdate(id);
        verifyNoMoreInteractions(catalog);
    }

    @Test
    void changed_binding_is_rejected_after_acquiring_locks() {
        UUID id = UUID.randomUUID();
        var group = OptionGroup.builder().id(groupId).name("RAM").displayName("RAM")
                .attributeId(new AttributeDefinitionId(id)).build();
        givenGroup(group);
        when(catalog.findDefinitionForUpdate(id)).thenReturn(Optional.of(definition(id)));
        var changed = OptionGroup.builder().id(groupId).name("RAM").displayName("RAM")
                .attributeId(new AttributeDefinitionId(UUID.randomUUID())).build();
        when(optionGroups.findByIdForUpdate(groupId)).thenReturn(Optional.of(changed));

        assertThatThrownBy(() -> validator.lockGroupAndValidateValues(groupId, List.of(command(UUID.randomUUID()))))
                .isInstanceOf(AttributeDomainException.class).hasMessageContaining("binding changed");
        verify(catalog, never()).findValueIds(any(), any());
    }

    private void givenGroup(OptionGroup group) {
        when(optionGroups.findAttributeBinding(groupId))
                .thenReturn(Optional.of(new OptionGroupAttributeBinding(groupId, group.getAttributeId())));
        lenient().when(optionGroups.findByIdForUpdate(groupId)).thenReturn(Optional.of(group));
    }

    private AddOptionValueCommand command(UUID valueId) {
        return AddOptionValueCommand.builder().attributeValueId(valueId).build();
    }

    private AttributeDefinition definition(UUID id) {
        return new AttributeDefinition(new AttributeDefinitionId(id), "test_ram", "RAM", AttributeDataType.NUMBER, "GB");
    }
}
