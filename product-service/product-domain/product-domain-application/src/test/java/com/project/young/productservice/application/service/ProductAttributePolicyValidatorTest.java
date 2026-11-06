package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.AttributeDefinitionId;
import com.project.young.common.domain.valueobject.AttributeValueId;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.common.domain.valueobject.ProductAttributeId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductOptionGroupId;
import com.project.young.common.domain.valueobject.ProductOptionValueId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.application.port.output.AttributeCatalogPort;
import com.project.young.productservice.application.port.output.view.AttributeCategoryView;
import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.entity.AttributeValue;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.entity.OptionValue;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.entity.ProductAttribute;
import com.project.young.productservice.domain.entity.ProductOptionGroup;
import com.project.young.productservice.domain.entity.ProductOptionValue;
import com.project.young.productservice.domain.entity.ProductVariant;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.repository.OptionGroupRepository;
import com.project.young.productservice.domain.valueobject.AttributeDataType;
import com.project.young.productservice.domain.valueobject.CategoryAttributePolicy;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.OptionGroupAttributeBinding;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ProductAttributePolicyValidatorTest {
    @Mock AttributeCatalogPort catalog;
    @Mock OptionGroupRepository groups;
    @InjectMocks ProductAttributePolicyValidator validator;
    final UUID attributeId = UUID.randomUUID();
    final UUID valueId = UUID.randomUUID();
    final OptionGroupId groupId = new OptionGroupId(UUID.randomUUID());
    final OptionValueId optionId = new OptionValueId(UUID.randomUUID());
    final ProductOptionValueId selectedId = new ProductOptionValueId(UUID.randomUUID());
    final AttributeDefinition definition = new AttributeDefinition(new AttributeDefinitionId(attributeId), "memory", "Memory", AttributeDataType.ENUM, null);

    @BeforeEach
    void category_exists() {
        lenient().when(catalog.findCategory(1L)).thenReturn(Optional.of(new AttributeCategoryView(1L, false)));
    }

    @Test
    void draft_may_omit_required_attributes_but_active_product_may_not() {
        assertThatCode(() -> validator.validate(product(ProductStatus.DRAFT, List.of(), List.of(), List.of()), snapshot(true, global()))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(product(ProductStatus.ACTIVE, List.of(), List.of(), List.of()), snapshot(true, global())))
                .isInstanceOf(ProductDomainException.class).hasMessageContaining("required attribute");
    }

    @Test
    void a_fixed_value_satisfies_a_required_attribute() {
        canonicalValue(attributeId);
        assertThatCode(() -> validator.validate(product(ProductStatus.ACTIVE, List.of(fixed()), List.of(), List.of()), snapshot(true, global())))
                .doesNotThrowAnyException();
    }

    @Test
    void fixed_and_option_representations_cannot_coexist_even_in_draft() {
        canonicalValue(attributeId);
        assertThatThrownBy(() -> validator.validate(product(ProductStatus.DRAFT, List.of(fixed()), List.of(local(true)), List.of()), snapshot(false, global())))
                .isInstanceOf(ProductDomainException.class).hasMessageContaining("both a fixed specification");
    }

    @Test
    void canonical_values_from_another_definition_are_rejected() {
        canonicalValue(UUID.randomUUID());
        assertThatThrownBy(() -> validator.validate(product(ProductStatus.DRAFT, List.of(fixed()), List.of(), List.of()), snapshot(false, global())))
                .isInstanceOf(ProductDomainException.class).hasMessageContaining("does not belong");
    }

    @Test
    void an_empty_option_group_cannot_satisfy_an_active_required_attribute() {
        assertThatThrownBy(() -> validator.validate(product(ProductStatus.ACTIVE, List.of(), List.of(local(false)), List.of()), snapshot(true, global())))
                .isInstanceOf(ProductDomainException.class).hasMessageContaining("required attribute");
    }

    @Test
    void every_active_variant_must_select_a_valid_required_option_value() {
        var invalid = ProductVariant.builder().id(new ProductVariantId(UUID.randomUUID())).sku("INVALID")
                .status(ProductStatus.ACTIVE).selectedOptionValues(Set.of()).build();
        assertThatThrownBy(() -> validator.validate(product(ProductStatus.ACTIVE, List.of(), List.of(local(true)), List.of(invalid)), snapshot(true, global())))
                .isInstanceOf(ProductDomainException.class).hasMessageContaining("Every active variant");
        var valid = ProductVariant.builder().id(new ProductVariantId(UUID.randomUUID())).sku("VALID")
                .status(ProductStatus.ACTIVE).selectedOptionValues(Set.of(selectedId)).build();
        assertThatCode(() -> validator.validate(product(ProductStatus.ACTIVE, List.of(), List.of(local(true)), List.of(valid)), snapshot(true, global())))
                .doesNotThrowAnyException();
    }

    @Test
    void a_disabled_category_policy_cannot_supply_fixed_specifications() {
        var snapshot = new ProductAttributePolicyValidator.LockedPolicyContext(1L,
                List.of(new CategoryAttributePolicy(1L, attributeId, false, false, false, false, 0)), Map.of(attributeId, definition), Map.of());
        assertThatThrownBy(() -> validator.validate(product(ProductStatus.DRAFT, List.of(fixed()), List.of(), List.of()), snapshot))
                .isInstanceOf(ProductDomainException.class).hasMessageContaining("does not allow");
    }

    @Test
    void all_definitions_are_locked_before_any_group() {
        when(catalog.findPolicyAttributeIds(1L)).thenReturn(Set.of(attributeId));
        when(catalog.findPoliciesByCategoryId(1L)).thenReturn(List.of(new CategoryAttributePolicy(1L, attributeId, true, false, true, false, 0)));
        when(groups.findAttributeBindings(Set.of(groupId))).thenReturn(List.of(new OptionGroupAttributeBinding(groupId, definition.getId())));
        when(catalog.findDefinitionsForUpdate(Set.of(attributeId))).thenReturn(List.of(definition));
        when(groups.findAllByIdsForUpdate(Set.of(groupId))).thenReturn(List.of(global()));
        validator.loadAndLockPolicyContext(product(ProductStatus.DRAFT, List.of(), List.of(local(true)), List.of()), Set.of(), Set.of());
        var ordered = inOrder(catalog, groups);
        ordered.verify(groups).findAttributeBindings(Set.of(groupId));
        ordered.verify(catalog).findDefinitionsForUpdate(Set.of(attributeId));
        ordered.verify(groups).findAllByIdsForUpdate(Set.of(groupId));
    }

    @Test
    void a_changed_group_binding_is_rejected_even_if_both_definitions_are_locked() {
        var other = new AttributeDefinition(new AttributeDefinitionId(UUID.randomUUID()), "storage", "Storage", AttributeDataType.ENUM, null);
        when(catalog.findPolicyAttributeIds(1L)).thenReturn(Set.of(attributeId, other.getId().getValue()));
        when(groups.findAttributeBindings(Set.of(groupId))).thenReturn(List.of(new OptionGroupAttributeBinding(groupId, definition.getId())));
        when(catalog.findDefinitionsForUpdate(Set.of(attributeId, other.getId().getValue()))).thenReturn(List.of(definition, other));
        when(groups.findAllByIdsForUpdate(Set.of(groupId))).thenReturn(List.of(OptionGroup.builder()
                .id(groupId).attributeId(other.getId()).name("MEMORY").displayName("Memory").build()));
        assertThatThrownBy(() -> validator.loadAndLockPolicyContext(product(ProductStatus.DRAFT, List.of(), List.of(local(true)), List.of()), Set.of(), Set.of()))
                .isInstanceOf(ProductDomainException.class).hasMessageContaining("binding changed");
    }

    @Test
    void multiple_groups_use_one_binding_lookup_and_one_ordered_lock_batch() {
        var secondId = new OptionGroupId(UUID.randomUUID());
        var other = new AttributeDefinition(new AttributeDefinitionId(UUID.randomUUID()), "storage", "Storage", AttributeDataType.ENUM, null);
        var requested = Set.of(groupId, secondId);
        var definitions = Set.of(attributeId, other.getId().getValue());
        when(groups.findAttributeBindings(requested)).thenReturn(List.of(
                new OptionGroupAttributeBinding(groupId, definition.getId()), new OptionGroupAttributeBinding(secondId, other.getId())));
        when(catalog.findDefinitionsForUpdate(definitions)).thenReturn(List.of(definition, other));
        when(groups.findAllByIdsForUpdate(requested)).thenReturn(List.of(global(), OptionGroup.builder()
                .id(secondId).attributeId(other.getId()).name("STORAGE").displayName("Storage").build()));

        var snapshot = validator.loadAndLockPolicyContext(product(ProductStatus.DRAFT, List.of(), List.of(), List.of()), Set.of(), requested);

        assertThat(snapshot.catalogOptionGroupsById()).containsOnlyKeys(groupId, secondId);
        var ordered = inOrder(catalog, groups);
        ordered.verify(groups).findAttributeBindings(requested);
        ordered.verify(catalog).findDefinitionsForUpdate(definitions);
        ordered.verify(groups).findAllByIdsForUpdate(requested);
        verify(groups, never()).findAttributeBinding(any());
        verify(groups, never()).findByIdForUpdate(any());
    }

    @Test
    void a_missing_binding_rejects_the_batch_before_acquiring_definition_locks() {
        when(groups.findAttributeBindings(Set.of(groupId))).thenReturn(List.of());
        assertThatThrownBy(() -> validator.loadAndLockPolicyContext(product(ProductStatus.DRAFT, List.of(), List.of(), List.of()), Set.of(), Set.of(groupId)))
                .isInstanceOf(ProductDomainException.class).hasMessageContaining("group not found");
        verify(catalog, never()).findDefinitionsForUpdate(any());
        verify(groups, never()).findAllByIdsForUpdate(any());
    }

    @Test
    void a_missing_locked_group_rejects_the_batch_without_partial_results() {
        when(groups.findAttributeBindings(Set.of(groupId))).thenReturn(List.of(new OptionGroupAttributeBinding(groupId, definition.getId())));
        when(catalog.findDefinitionsForUpdate(Set.of(attributeId))).thenReturn(List.of(definition));
        when(groups.findAllByIdsForUpdate(Set.of(groupId))).thenReturn(List.of());
        assertThatThrownBy(() -> validator.loadAndLockPolicyContext(product(ProductStatus.DRAFT, List.of(), List.of(), List.of()), Set.of(), Set.of(groupId)))
                .isInstanceOf(ProductDomainException.class).hasMessageContaining("group not found");
    }

    private void canonicalValue(UUID owner) {
        when(catalog.findValuesByIds(any())).thenReturn(List.of(new AttributeValue(new AttributeValueId(valueId),
                new AttributeDefinitionId(owner), AttributeDataType.ENUM, "16 GB", "16", null, null, null)));
    }

    private ProductAttribute fixed() {
        return new ProductAttribute(new ProductAttributeId(UUID.randomUUID()), definition.getId(), new AttributeValueId(valueId));
    }

    private OptionGroup global() {
        return OptionGroup.builder().id(groupId).attributeId(definition.getId()).name("MEMORY").displayName("Memory")
                .optionValues(List.of(OptionValue.builder().id(optionId).attributeValueId(new AttributeValueId(valueId)).value("16").displayName("16 GB").build())).build();
    }

    private ProductOptionGroup local(boolean withValue) {
        return ProductOptionGroup.builder().id(new ProductOptionGroupId(UUID.randomUUID())).optionGroupId(groupId).stepOrder(1024)
                .optionValues(withValue ? List.of(ProductOptionValue.builder().id(selectedId).optionValueId(optionId).status(OptionStatus.ACTIVE).build()) : List.of()).build();
    }

    private ProductAttributePolicyValidator.LockedPolicyContext snapshot(boolean required, OptionGroup group) {
        return new ProductAttributePolicyValidator.LockedPolicyContext(1L,
                List.of(new CategoryAttributePolicy(1L, attributeId, true, required, true, true, 0)),
                Map.of(attributeId, definition), Map.of(groupId, group));
    }

    private Product product(ProductStatus status, List<ProductAttribute> attributes, List<ProductOptionGroup> options, List<ProductVariant> variants) {
        return Product.builder().productId(new ProductId(UUID.randomUUID())).categoryId(new CategoryId(1L)).name("Laptop")
                .description("Laptop for product attribute policy tests").brand("Brand").conditionType(ConditionType.NEW)
                .mainImageUrl("https://example.com/laptop.jpg").basePrice(new Money(java.math.BigDecimal.TEN))
                .status(status).attributes(attributes).optionGroups(options).variants(variants).build();
    }
}
