package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.common.domain.valueobject.AttributeValueId;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.command.AddProductOptionValueCommand;
import com.project.young.productservice.application.port.output.AttributeCatalogPort;
import com.project.young.productservice.application.port.output.view.AttributeCategoryView;
import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.entity.OptionValue;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.repository.OptionGroupRepository;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import com.project.young.productservice.domain.valueobject.CategoryAttributePolicy;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.OptionGroupAttributeBinding;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class ProductOptionPolicyValidatorTest {
    @Mock
    AttributeCatalogPort catalog;
    @Mock
    OptionGroupRepository optionGroups;
    @InjectMocks
    ProductOptionPolicyValidator validator;

    final UUID attributeId = UUID.randomUUID();
    final OptionGroupId groupId = new OptionGroupId(UUID.randomUUID());
    final OptionValueId valueId = new OptionValueId(UUID.randomUUID());
    final AttributeValueId canonicalId = new AttributeValueId(UUID.randomUUID());
    Product product;
    AttributeDefinition definition;

    @BeforeEach
    void setup() {
        product = Product.builder().productId(new ProductId(UUID.randomUUID())).categoryId(new CategoryId(1L))
                .name("Product").description("Product description for validation tests")
                .basePrice(new Money(BigDecimal.ONE))
                .brand("Brand").conditionType(ConditionType.NEW)
                .mainImageUrl("https://example.com/product.jpg").build();
        definition = new AttributeDefinition(new AttributeDefinitionId(attributeId), "test_memory", "Memory",
                AttributeDataType.NUMBER, "GB");
        lenient().when(catalog.findCategory(1L)).thenReturn(Optional.of(new AttributeCategoryView(1L, false)));
        lenient().when(optionGroups.findAttributeBinding(groupId))
                .thenReturn(Optional.of(new OptionGroupAttributeBinding(groupId, new AttributeDefinitionId(attributeId))));
        lenient().when(optionGroups.findByIdForUpdate(groupId)).thenReturn(Optional.of(group(OptionStatus.ACTIVE,
                new AttributeDefinitionId(attributeId), List.of(value(OptionStatus.ACTIVE, canonicalId)))));
        lenient().when(catalog.findDefinitionForUpdate(attributeId)).thenReturn(Optional.of(definition));
        lenient().when(catalog.findPolicy(1L, attributeId)).thenReturn(Optional.of(policy(true, true)));
        lenient().when(catalog.findValueIds(eq(attributeId), any())).thenReturn(Set.of(canonicalId.getValue()));
    }

    @Test
    void valid_options_validate_canonical_values_in_batch_with_locks() {
        var secondId = new OptionValueId(UUID.randomUUID());
        var secondCanonical = new AttributeValueId(UUID.randomUUID());
        var second = OptionValue.reconstitute(secondId, "RAM32", "32 GB", 1, OptionStatus.ACTIVE, secondCanonical);
        when(optionGroups.findByIdForUpdate(groupId)).thenReturn(Optional.of(group(OptionStatus.ACTIVE,
                new AttributeDefinitionId(attributeId), List.of(value(OptionStatus.ACTIVE, canonicalId), second))));
        Set<UUID> ids = Set.of(canonicalId.getValue(), secondCanonical.getValue());
        when(catalog.findValueIds(attributeId, ids)).thenReturn(ids);

        assertThatCode(() -> validator.validate(product, groupId, List.of(command(valueId.getValue()), command(secondId.getValue()))))
                .doesNotThrowAnyException();
        var ordered = inOrder(optionGroups, catalog);
        ordered.verify(optionGroups).findAttributeBinding(groupId);
        ordered.verify(catalog).findDefinitionForUpdate(attributeId);
        ordered.verify(optionGroups).findByIdForUpdate(groupId);
        verify(catalog).findValueIds(attributeId, ids);
        verify(catalog, never()).findValue(any(), any());
    }

    @Test
    void category_not_assigned_rejects_before_querying() {
        product.changeCategoryId(null);
        rejects();
        verifyNoInteractions(catalog, optionGroups);
    }

    @Test
    void category_deleted_rejects_before_loading_group() {
        when(catalog.findCategory(1L)).thenReturn(Optional.of(new AttributeCategoryView(1L, true)));
        rejects();
        verifyNoInteractions(optionGroups);
    }

    @Test
    void category_not_found_rejects_options() {
        when(catalog.findCategory(1L)).thenReturn(Optional.empty());
        rejects();
    }

    @Test
    void dictionary_group_not_found_rejects_options() {
        when(optionGroups.findAttributeBinding(groupId)).thenReturn(Optional.empty());
        rejects();
    }

    @ParameterizedTest
    @EnumSource(value = OptionStatus.class, names = {"INACTIVE", "DELETED"})
    void dictionary_group_not_active_rejects_options(OptionStatus status) {
        when(optionGroups.findByIdForUpdate(groupId)).thenReturn(Optional.of(group(status,
                new AttributeDefinitionId(attributeId), List.of(value(OptionStatus.ACTIVE, canonicalId)))));
        rejects();
    }

    @Test
    void dictionary_group_without_attribute_mapping_rejects_options() {
        when(optionGroups.findAttributeBinding(groupId))
                .thenReturn(Optional.of(new OptionGroupAttributeBinding(groupId, null)));
        rejects();
        verify(catalog, never()).findDefinitionForUpdate(any());
    }

    @Test
    void attribute_definition_inactive_rejects_options() {
        when(catalog.findDefinitionForUpdate(attributeId)).thenReturn(Optional.of(definition.deactivate()));
        rejects();
        verify(optionGroups, never()).findByIdForUpdate(any());
    }

    @Test
    void attribute_definition_not_found_rejects_options() {
        when(catalog.findDefinitionForUpdate(attributeId)).thenReturn(Optional.empty());
        rejects();
    }

    @Test
    void explicit_policy_missing_rejects_without_reading_ancestor_policies() {
        when(catalog.findPolicy(1L, attributeId)).thenReturn(Optional.empty());
        rejects();
        verify(catalog, never()).findPoliciesByCategoryId(any());
    }

    @Test
    void category_policy_disabled_rejects_options() {
        when(catalog.findPolicy(1L, attributeId)).thenReturn(Optional.of(policy(false, false)));
        rejects();
    }

    @Test
    void category_policy_disallows_options_rejects_options() {
        when(catalog.findPolicy(1L, attributeId)).thenReturn(Optional.of(policy(true, false)));
        rejects();
    }

    @ParameterizedTest
    @EnumSource(value = OptionStatus.class, names = {"INACTIVE", "DELETED"})
    void dictionary_value_not_active_rejects_options(OptionStatus status) {
        when(optionGroups.findByIdForUpdate(groupId)).thenReturn(Optional.of(group(OptionStatus.ACTIVE,
                new AttributeDefinitionId(attributeId), List.of(value(status, canonicalId)))));
        rejects();
    }

    @Test
    void option_value_from_another_group_rejects_options() {
        assertThatThrownBy(() -> validator.validate(product, groupId, List.of(command(UUID.randomUUID()))))
                .isInstanceOf(ProductDomainException.class);
    }

    @Test
    void dictionary_value_without_canonical_mapping_rejects_options() {
        when(optionGroups.findByIdForUpdate(groupId)).thenReturn(Optional.of(group(OptionStatus.ACTIVE,
                new AttributeDefinitionId(attributeId), List.of(value(OptionStatus.ACTIVE, null)))));
        rejects();
    }

    @Test
    void canonical_value_from_another_attribute_rejects_options() {
        when(catalog.findValueIds(attributeId, Set.of(canonicalId.getValue()))).thenReturn(Set.of());
        rejects();
    }

    @Test
    void duplicate_or_null_option_value_ids_reject_before_querying() {
        var command = command(valueId.getValue());
        assertThatThrownBy(() -> validator.validate(product, groupId, List.of(command, command)))
                .isInstanceOf(ProductDomainException.class);
        assertThatThrownBy(() -> validator.validate(product, groupId, List.of(command(null))))
                .isInstanceOf(ProductDomainException.class);
        verifyNoInteractions(catalog, optionGroups);
    }

    @Test
    void changed_binding_is_rejected_after_acquiring_locks() {
        when(optionGroups.findByIdForUpdate(groupId)).thenReturn(Optional.of(group(OptionStatus.ACTIVE,
                new AttributeDefinitionId(UUID.randomUUID()), List.of(value(OptionStatus.ACTIVE, canonicalId)))));
        rejects();
        verify(catalog, never()).findPolicy(any(), any());
    }

    @Test
    void group_disappearing_after_parent_lookup_rejects_options() {
        when(optionGroups.findByIdForUpdate(groupId)).thenReturn(Optional.empty());
        rejects();
    }

    private void rejects() {
        assertThatThrownBy(() -> validator.validate(product, groupId, List.of(command(valueId.getValue()))))
                .isInstanceOf(ProductDomainException.class);
    }

    private CategoryAttributePolicy policy(boolean enabled, boolean allowed) {
        return new CategoryAttributePolicy(1L, attributeId, enabled, false, allowed, false, 0);
    }

    private OptionGroup group(OptionStatus status, AttributeDefinitionId id, List<OptionValue> values) {
        return OptionGroup.reconstitute(groupId, "RAM", "Memory", status, values, id);
    }

    private OptionValue value(OptionStatus status, AttributeValueId id) {
        return OptionValue.reconstitute(valueId, "RAM16", "16 GB", 0, status, id);
    }

    private AddProductOptionValueCommand command(UUID id) {
        return AddProductOptionValueCommand.builder().optionValueId(id).build();
    }
}
