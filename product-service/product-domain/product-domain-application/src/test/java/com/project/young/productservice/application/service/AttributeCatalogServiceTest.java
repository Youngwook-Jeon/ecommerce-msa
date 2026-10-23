package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.productservice.application.dto.command.CreateAttributeDefinitionCommand;
import com.project.young.productservice.application.dto.command.CreateAttributeValueCommand;
import com.project.young.productservice.application.dto.command.SetCategoryAttributeCommand;
import com.project.young.productservice.application.port.output.AttributeCatalogPort;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.application.port.output.view.AttributeCategoryView;
import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.exception.AttributeDomainException;
import com.project.young.productservice.domain.exception.AttributeNotFoundException;
import com.project.young.productservice.domain.exception.AttributeInactiveException;
import com.project.young.productservice.domain.exception.AttributeInUseException;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import com.project.young.productservice.domain.valueobject.CategoryAttributePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;

class AttributeCatalogServiceTest {
    private final AttributeCatalogPort catalog = mock(AttributeCatalogPort.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final AttributeCatalogService service = new AttributeCatalogService(catalog, idGenerator);
    private final UUID attributeId = UUID.randomUUID();

    @Test
    void createsDefinitionWithIdFromGenerator() {
        when(idGenerator.generateId()).thenReturn(attributeId);

        var result = service.createDefinition(new CreateAttributeDefinitionCommand(
                "ram_capacity", "Memory", AttributeDataType.NUMBER, "GB"));

        assertThat(result.getId().getValue()).isEqualTo(attributeId);
        verify(idGenerator).generateId();
        verify(catalog).insertDefinition(result);
    }

    @Test
    void createsValueWithIdFromGenerator() {
        UUID valueId = UUID.randomUUID();
        when(catalog.findDefinitionForUpdate(attributeId)).thenReturn(Optional.of(
                new AttributeDefinition(new AttributeDefinitionId(attributeId), "color", "Color", AttributeDataType.ENUM, null)));
        when(idGenerator.generateId()).thenReturn(valueId);

        var result = service.createValue(attributeId,
                new CreateAttributeValueCommand("Black", "BLACK", null, null, null));

        assertThat(result.getId().getValue()).isEqualTo(valueId);
        assertThat(result.getAttributeId().getValue()).isEqualTo(attributeId);
        verify(idGenerator).generateId();
        verify(catalog).insertValue(result);
    }

    @Test
    void queriesOnlyRequestedCategoryPolicies() {
        when(catalog.findCategory(2L)).thenReturn(Optional.of(new AttributeCategoryView(2L, false)));
        when(catalog.findPoliciesByCategoryId(2L)).thenReturn(List.of(
                new CategoryAttributePolicy(2L, attributeId, true, true, true, true, 10)));
        when(catalog.findDefinitionsByIds(Set.of(attributeId))).thenReturn(List.of(
                new AttributeDefinition(new AttributeDefinitionId(attributeId), "ram_capacity", "Memory", AttributeDataType.NUMBER, "GB")));

        var result = service.categoryPolicies(2L).getFirst();

        assertThat(result.policy().categoryId()).isEqualTo(2L);
        verify(catalog).findPoliciesByCategoryId(2L);
        verify(catalog).findDefinitionsByIds(Set.of(attributeId));
        verify(catalog, never()).findCategory(1L);
        verify(catalog, never()).findDefinitions();
    }

    @Test
    void emptyPoliciesSkipDefinitionQueries() {
        when(catalog.findCategory(2L)).thenReturn(Optional.of(new AttributeCategoryView(2L, false)));
        when(catalog.findPoliciesByCategoryId(2L)).thenReturn(List.of());

        assertThat(service.categoryPolicies(2L)).isEmpty();

        verify(catalog, never()).findDefinitions();
        verify(catalog, never()).findDefinitionsByIds(any());
    }

    @Test
    void localPoliciesBatchOnlyReferencedDefinitionsAndKeepInactiveMetadata() {
        UUID otherId = UUID.randomUUID();
        when(catalog.findCategory(2L)).thenReturn(Optional.of(new AttributeCategoryView(2L, false)));
        when(catalog.findPoliciesByCategoryId(2L)).thenReturn(List.of(
                new CategoryAttributePolicy(2L, attributeId, true, false, true, true, 20),
                new CategoryAttributePolicy(2L, otherId, true, false, false, true, 10)));
        when(catalog.findDefinitionsByIds(Set.of(attributeId, otherId))).thenReturn(List.of(
                new AttributeDefinition(new AttributeDefinitionId(attributeId), "ram_capacity", "Memory", AttributeDataType.NUMBER, "GB"),
                new AttributeDefinition(new AttributeDefinitionId(otherId), "weight", "Weight", AttributeDataType.NUMBER, "g").deactivate()));

        var results = service.categoryPolicies(2L);

        assertThat(results).extracting(result -> result.attribute().getCode()).containsExactly("weight", "ram_capacity");
        assertThat(results.getFirst().usable()).isFalse();
        verify(catalog).findDefinitionsByIds(Set.of(attributeId, otherId));
        verify(catalog, never()).findDefinitions();
    }

    @Test
    void missingDefinitionRejectsEntireResult() {
        UUID missingId = UUID.randomUUID();
        when(catalog.findCategory(2L)).thenReturn(Optional.of(new AttributeCategoryView(2L, false)));
        when(catalog.findPoliciesByCategoryId(2L)).thenReturn(List.of(
                new CategoryAttributePolicy(2L, attributeId, true, false, true, true, 10),
                new CategoryAttributePolicy(2L, missingId, true, true, false, true, 20)));
        when(catalog.findDefinitionsByIds(Set.of(attributeId, missingId))).thenReturn(List.of(
                new AttributeDefinition(new AttributeDefinitionId(attributeId), "ram_capacity", "Memory", AttributeDataType.NUMBER, "GB")));

        String expectedMessage = "Attribute definition missing for category policy: attributeId=" + missingId;
        assertThatThrownBy(() -> service.categoryPolicies(2L))
                .isInstanceOf(IllegalStateException.class).hasMessage(expectedMessage);
    }

    @Test
    void missingPolicyCategoryRejectsEntireLocalResult() {
        when(catalog.findCategory(2L)).thenReturn(Optional.of(new AttributeCategoryView(2L, false)));
        when(catalog.findPoliciesByCategoryId(2L)).thenReturn(List.of(
                new CategoryAttributePolicy(2L, attributeId, true, false, true, true, 10),
                new CategoryAttributePolicy(999L, attributeId, true, false, true, true, 20)));
        when(catalog.findDefinitionsByIds(Set.of(attributeId))).thenReturn(List.of(
                new AttributeDefinition(new AttributeDefinitionId(attributeId), "ram_capacity", "Memory", AttributeDataType.NUMBER, "GB")));

        assertThatThrownBy(() -> service.categoryPolicies(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Category policy does not belong to requested category: categoryId=2, policyCategoryId=999");
    }

    @Test
    void rejectsInvalidMissingOrDeletedCategories() {
        assertThatThrownBy(() -> service.categoryPolicies(null)).isInstanceOf(AttributeDomainException.class);
        assertThatThrownBy(() -> service.categoryPolicies(0L)).isInstanceOf(AttributeDomainException.class);
        assertThatThrownBy(() -> service.categoryPolicies(999L)).isInstanceOf(AttributeNotFoundException.class);
        when(catalog.findCategory(3L)).thenReturn(Optional.of(new AttributeCategoryView(3L, true)));
        assertThatThrownBy(() -> service.categoryPolicies(3L)).isInstanceOf(AttributeNotFoundException.class);
    }

    @Test
    void rejectsUnknownAttributeAndContradictoryDisabledPolicyBeforeSaving() {
        when(catalog.findCategory(1L)).thenReturn(Optional.of(new AttributeCategoryView(1L, false)));
        var command = new SetCategoryAttributeCommand(false, true, false, false, 0);
        assertThatThrownBy(() -> service.setPolicy(1L, attributeId, command))
                .isInstanceOf(AttributeNotFoundException.class);
        when(catalog.findDefinitionForUpdate(attributeId)).thenReturn(Optional.of(
                new AttributeDefinition(new AttributeDefinitionId(attributeId), "ram_capacity", "Memory", AttributeDataType.NUMBER, "GB")));
        assertThatThrownBy(() -> service.setPolicy(1L, attributeId, command))
                .isInstanceOf(AttributeDomainException.class);
        verify(catalog, never()).savePolicy(any());
    }

    @Test
    void deactivationPreservesIdentityAndRenameDoesNotReactivate() {
        var original = new AttributeDefinition(new AttributeDefinitionId(attributeId), "ram_capacity", "Memory", AttributeDataType.NUMBER, "GB");
        when(catalog.findDefinitionForUpdate(attributeId)).thenReturn(Optional.of(original));

        var inactive = service.deactivateDefinition(attributeId);
        assertThat(inactive.isActive()).isFalse();
        assertThat(inactive.getId().getValue()).isEqualTo(attributeId);
        verify(catalog).updateDefinition(inactive);

        when(catalog.findDefinitionForUpdate(attributeId)).thenReturn(Optional.of(inactive));
        assertThat(service.renameDefinition(attributeId, "Renamed memory").isActive()).isFalse();
        assertThat(service.deactivateDefinition(attributeId)).isEqualTo(inactive);
        verifyNoInteractions(idGenerator);
    }

    @Test
    void inactiveDefinitionsRejectNewValuesAndEnabledPoliciesButAllowExclusions() {
        var inactive = new AttributeDefinition(new AttributeDefinitionId(attributeId), "ram_capacity", "Memory", AttributeDataType.NUMBER, "GB").deactivate();
        when(catalog.findDefinitionForUpdate(attributeId)).thenReturn(Optional.of(inactive));
        when(catalog.findCategory(1L)).thenReturn(Optional.of(new AttributeCategoryView(1L, false)));
        when(catalog.savePolicy(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.createValue(attributeId,
                new CreateAttributeValueCommand("32 GB", null, BigDecimal.valueOf(32), null, null)))
                .isInstanceOf(AttributeInactiveException.class);
        assertThatThrownBy(() -> service.setPolicy(1L, attributeId,
                new SetCategoryAttributeCommand(true, false, false, true, 0)))
                .isInstanceOf(AttributeInactiveException.class);
        verify(catalog, never()).insertValue(any());
        verifyNoInteractions(idGenerator);
        assertThat(service.setPolicy(1L, attributeId,
                new SetCategoryAttributeCommand(false, false, false, false, 0)).enabled()).isFalse();
    }

    @Test
    void referencedDefinitionsCannotBePhysicallyDeleted() {
        when(catalog.findDefinitionForUpdate(attributeId)).thenReturn(Optional.of(
                new AttributeDefinition(new AttributeDefinitionId(attributeId), "color", "Color", AttributeDataType.ENUM, null)));
        when(catalog.hasDefinitionReferences(attributeId)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteDefinition(attributeId)).isInstanceOf(AttributeInUseException.class);
        verify(catalog, never()).deleteDefinition(any());
    }
}
