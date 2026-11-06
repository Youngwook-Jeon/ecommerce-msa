package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.BaseId;
import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.productservice.application.port.output.AttributeCatalogPort;
import com.project.young.productservice.application.port.output.view.AttributeCategoryView;
import com.project.young.productservice.domain.entity.AttributeDefinition;
import com.project.young.productservice.domain.entity.AttributeValue;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.entity.ProductAttribute;
import com.project.young.productservice.domain.entity.ProductOptionGroup;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.repository.OptionGroupRepository;
import com.project.young.productservice.domain.valueobject.CategoryAttributePolicy;
import com.project.young.productservice.domain.valueobject.OptionGroupAttributeBinding;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Validates the proposed product configuration before catalog version advancement and persistence. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ProductAttributePolicyValidator {
    private final AttributeCatalogPort catalog;
    private final OptionGroupRepository optionGroups;

    public ProductAttributePolicyValidator(AttributeCatalogPort catalog, OptionGroupRepository optionGroups) {
        this.catalog = catalog;
        this.optionGroups = optionGroups;
    }

    /**
     * Discover scalar IDs before loading managed entities. Acquire every definition lock before
     * group locks, then revalidate bindings and read category policy flags under those locks.
     */
    public LockedPolicyContext loadAndLockPolicyContext(Product product, Set<UUID> additionalAttributeIds,
                                                        Set<OptionGroupId> additionalOptionGroupIds) {
        Long categoryId = product.getCategoryId().map(BaseId::getValue).orElse(null);
        Set<UUID> categoryAttributeIds = categoryId == null ? Set.of() : catalog.findPolicyAttributeIds(categoryId);
        var optionGroupIds = collectOptionGroupIds(product, additionalOptionGroupIds);
        var bindings = loadRequiredBindings(optionGroupIds);
        var attributeIds = collectAttributeIds(product, categoryAttributeIds, bindings, additionalAttributeIds);

        var definitionsById = lockRequiredDefinitions(attributeIds);
        var catalogOptionGroupsById = lockRequiredOptionGroups(optionGroupIds);
        validateUnchangedBindings(bindings, catalogOptionGroupsById);
        var categoryPolicies = loadPoliciesAfterDefinitionLocks(categoryId, definitionsById);

        return new LockedPolicyContext(categoryId, categoryPolicies, definitionsById, catalogOptionGroupsById);
    }

    public void validate(Product product) {
        validate(product, loadAndLockPolicyContext(product, Set.of(), Set.of()));
    }

    public void validate(Product product, LockedPolicyContext policyContext) {
        if (product.isDeleted()) {
            return;
        }
        var context = loadValidationContext(product, policyContext);
        var fixedAttributeIds = validateFixedSpecifications(product, context);
        var optionGroupsByAttributeId = validatePurchaseOptions(product, context, fixedAttributeIds);

        if (product.getStatus() == ProductStatus.ACTIVE) {
            validateRequiredAttributes(product, context, fixedAttributeIds, optionGroupsByAttributeId);
        }
    }

    private Set<OptionGroupId> collectOptionGroupIds(Product product, Set<OptionGroupId> additionalOptionGroupIds) {
        Set<OptionGroupId> optionGroupIds = new HashSet<>(additionalOptionGroupIds);
        product.getOptionGroups().stream()
                .filter(group -> !group.getStatus().isDeleted())
                .forEach(group -> optionGroupIds.add(group.getOptionGroupId()));
        return optionGroupIds;
    }

    private Map<OptionGroupId, OptionGroupAttributeBinding> loadRequiredBindings(Set<OptionGroupId> optionGroupIds) {
        if (optionGroupIds.isEmpty()) {
            return Map.of();
        }
        var bindings = optionGroups.findAttributeBindings(optionGroupIds).stream()
                .collect(Collectors.toMap(OptionGroupAttributeBinding::groupId, Function.identity()));
        if (!bindings.keySet().containsAll(optionGroupIds)) {
            throw new ProductDomainException("Global option group not found");
        }
        return bindings;
    }

    private Set<UUID> collectAttributeIds(Product product, Set<UUID> categoryAttributeIds,
                                         Map<OptionGroupId, OptionGroupAttributeBinding> bindings,
                                         Set<UUID> additionalAttributeIds) {
        Set<UUID> attributeIds = new HashSet<>(additionalAttributeIds);
        attributeIds.addAll(categoryAttributeIds);
        product.getAttributes().forEach(attribute -> attributeIds.add(attribute.getAttributeId().getValue()));
        bindings.values().stream()
                .filter(binding -> binding.attributeId() != null)
                .forEach(binding -> attributeIds.add(binding.attributeId().getValue()));
        return attributeIds;
    }

    private Map<UUID, AttributeDefinition> lockRequiredDefinitions(Set<UUID> attributeIds) {
        var definitionsById = catalog.findDefinitionsForUpdate(attributeIds).stream()
                .collect(Collectors.toMap(definition -> definition.getId().getValue(), Function.identity()));
        if (!definitionsById.keySet().containsAll(attributeIds)) {
            throw new ProductDomainException("Product attribute definition not found");
        }
        return definitionsById;
    }

    private Map<OptionGroupId, OptionGroup> lockRequiredOptionGroups(Set<OptionGroupId> optionGroupIds) {
        if (optionGroupIds.isEmpty()) {
            return Map.of();
        }
        var catalogOptionGroupsById = optionGroups.findAllByIdsForUpdate(optionGroupIds).stream()
                .collect(Collectors.toMap(OptionGroup::getId, Function.identity()));
        if (!catalogOptionGroupsById.keySet().containsAll(optionGroupIds)) {
            throw new ProductDomainException("Global option group not found");
        }
        return catalogOptionGroupsById;
    }

    private void validateUnchangedBindings(Map<OptionGroupId, OptionGroupAttributeBinding> bindings,
                                           Map<OptionGroupId, OptionGroup> catalogOptionGroupsById) {
        for (var catalogOptionGroup : catalogOptionGroupsById.values()) {
            var originalBinding = bindings.get(catalogOptionGroup.getId());
            if (!Objects.equals(originalBinding.attributeId(), catalogOptionGroup.getAttributeId())) {
                throw new ProductDomainException("Option attribute binding changed; reload the product");
            }
        }
    }

    private List<CategoryAttributePolicy> loadPoliciesAfterDefinitionLocks(Long categoryId,
                                                                          Map<UUID, AttributeDefinition> definitionsById) {
        var categoryPolicies = categoryId == null ? List.<CategoryAttributePolicy>of() : catalog.findPoliciesByCategoryId(categoryId);
        if (categoryPolicies.stream().anyMatch(policy -> !definitionsById.containsKey(policy.attributeId()))) {
            throw new ProductDomainException("Category attribute policies changed; reload before editing the product");
        }
        return categoryPolicies;
    }

    /** Load values from the proposed product state, including newly assigned specifications. */
    private ValidationContext loadValidationContext(Product product, LockedPolicyContext policyContext) {
        var category = policyContext.categoryId() == null ? null : catalog.findCategory(policyContext.categoryId()).orElse(null);
        validateCategory(product, policyContext.categoryId(), category);
        var policiesByAttributeId = policyContext.categoryPolicies().stream()
                .collect(Collectors.toMap(CategoryAttributePolicy::attributeId, Function.identity()));
        var canonicalValueIds = product.getAttributes().stream()
                .map(attribute -> attribute.getAttributeValueId().getValue()).collect(Collectors.toSet());
        var canonicalValuesById = catalog.findValuesByIds(canonicalValueIds).stream()
                .collect(Collectors.toMap(value -> value.getId().getValue(), Function.identity()));
        return new ValidationContext(policyContext.categoryPolicies(), policiesByAttributeId,
                policyContext.definitionsById(), policyContext.catalogOptionGroupsById(), canonicalValuesById);
    }

    private void validateCategory(Product product, Long categoryId, AttributeCategoryView category) {
        if (categoryId == null) {
            if (!product.getAttributes().isEmpty() || !product.getOptionGroups().isEmpty()) {
                throw new ProductDomainException("A category is required for product specifications and options");
            }
        } else if (category == null || category.deleted()) {
            throw new ProductDomainException("Product category is missing or deleted");
        }
    }

    private Set<UUID> validateFixedSpecifications(Product product, ValidationContext context) {
        Set<UUID> fixedAttributeIds = new HashSet<>();
        for (var specification : product.getAttributes()) {
            UUID attributeId = specification.getAttributeId().getValue();
            requireFixedSpecificationAllowed(attributeId, context);
            if (!fixedAttributeIds.add(attributeId)) {
                throw new ProductDomainException("Duplicate fixed product attribute");
            }
            validateSpecificationValue(specification, context);
        }
        return fixedAttributeIds;
    }

    private void validateSpecificationValue(ProductAttribute specification, ValidationContext context) {
        var canonicalValue = context.canonicalValuesById().get(specification.getAttributeValueId().getValue());
        var definition = context.definitionsById().get(specification.getAttributeId().getValue());
        if (canonicalValue == null || !canonicalValue.getAttributeId().equals(specification.getAttributeId())
                || canonicalValue.getDataType() != definition.getDataType()) {
            throw new ProductDomainException("Specification value does not belong to its attribute definition");
        }
    }

    private Map<UUID, ProductOptionGroup> validatePurchaseOptions(Product product, ValidationContext context,
                                                                 Set<UUID> fixedAttributeIds) {
        Map<UUID, ProductOptionGroup> optionGroupsByAttributeId = new HashMap<>();
        for (var productOptionGroup : product.getOptionGroups()) {
            if (productOptionGroup.getStatus().isDeleted()) {
                continue;
            }
            var catalogOptionGroup = context.catalogOptionGroupsById().get(productOptionGroup.getOptionGroupId());
            if (catalogOptionGroup == null) {
                throw new ProductDomainException("Global option group not found");
            }
            // Legacy groups are readable but cannot satisfy required semantic attributes.
            if (catalogOptionGroup.getAttributeId() == null) {
                continue;
            }
            UUID attributeId = catalogOptionGroup.getAttributeId().getValue();
            requirePurchaseOptionAllowed(attributeId, context);
            if (fixedAttributeIds.contains(attributeId)) {
                throw new ProductDomainException("An attribute cannot be both a fixed specification and a purchase option");
            }
            if (optionGroupsByAttributeId.put(attributeId, productOptionGroup) != null) {
                throw new ProductDomainException("Duplicate purchase option attribute");
            }
        }
        return optionGroupsByAttributeId;
    }

    private void validateRequiredAttributes(Product product, ValidationContext context, Set<UUID> fixedAttributeIds,
                                            Map<UUID, ProductOptionGroup> optionGroupsByAttributeId) {
        for (var policy : context.categoryPolicies()) {
            if (!isRequiredActiveAttribute(policy, context) || fixedAttributeIds.contains(policy.attributeId())) {
                continue;
            }
            var productOptionGroup = optionGroupsByAttributeId.get(policy.attributeId());
            var validProductOptionValueIds = requireUsableRequiredOptionValues(productOptionGroup, context);
            validateActiveVariantSelections(product, validProductOptionValueIds);
        }
    }

    private boolean isRequiredActiveAttribute(CategoryAttributePolicy policy, ValidationContext context) {
        var definition = context.definitionsById().get(policy.attributeId());
        return policy.enabled() && policy.required() && definition != null && definition.isActive();
    }

    private Set<UUID> requireUsableRequiredOptionValues(ProductOptionGroup productOptionGroup, ValidationContext context) {
        if (productOptionGroup == null || !productOptionGroup.isActive()) {
            throw missingRequiredAttribute();
        }
        var catalogOptionGroup = context.catalogOptionGroupsById().get(productOptionGroup.getOptionGroupId());
        if (catalogOptionGroup.getStatus() != OptionStatus.ACTIVE) {
            throw missingRequiredAttribute();
        }
        var validProductOptionValueIds = findValidProductOptionValueIds(productOptionGroup, catalogOptionGroup);
        if (validProductOptionValueIds.isEmpty()) {
            throw missingRequiredAttribute();
        }
        return validProductOptionValueIds;
    }

    private void validateActiveVariantSelections(Product product, Set<UUID> validProductOptionValueIds) {
        for (var variant : product.getVariants()) {
            if (variant.getStatus() != ProductStatus.ACTIVE) {
                continue;
            }
            long selectedValidValueCount = variant.getSelectedOptionValues().stream()
                    .filter(id -> validProductOptionValueIds.contains(id.getValue())).count();
            if (selectedValidValueCount != 1) {
                throw new ProductDomainException("Every active variant must select a valid value for each required option attribute");
            }
        }
    }

    private void requireFixedSpecificationAllowed(UUID attributeId, ValidationContext context) {
        requireActiveCategoryAttribute(attributeId, context);
    }

    private void requirePurchaseOptionAllowed(UUID attributeId, ValidationContext context) {
        var policy = requireActiveCategoryAttribute(attributeId, context);
        if (!policy.optionAllowed()) {
            throw new ProductDomainException("Product category does not allow this active attribute representation");
        }
    }

    private CategoryAttributePolicy requireActiveCategoryAttribute(UUID attributeId, ValidationContext context) {
        var definition = context.definitionsById().get(attributeId);
        var policy = context.policiesByAttributeId().get(attributeId);
        if (definition == null || !definition.isActive() || policy == null || !policy.enabled()) {
            throw new ProductDomainException("Product category does not allow this active attribute representation");
        }
        return policy;
    }

    private Set<UUID> findValidProductOptionValueIds(ProductOptionGroup productOptionGroup, OptionGroup catalogOptionGroup) {
        Set<UUID> activeCatalogOptionValueIds = catalogOptionGroup.getOptionValues().stream()
                .filter(value -> value.getStatus() == OptionStatus.ACTIVE && value.getAttributeValueId() != null)
                .map(value -> value.getId().getValue()).collect(Collectors.toSet());
        return productOptionGroup.getOptionValues().stream()
                .filter(value -> value.getStatus() == OptionStatus.ACTIVE)
                .filter(value -> activeCatalogOptionValueIds.contains(value.getOptionValueId().getValue()))
                .map(value -> value.getId().getValue()).collect(Collectors.toSet());
    }

    private ProductDomainException missingRequiredAttribute() {
        return new ProductDomainException("Active products must satisfy every required attribute with a fixed value or valid purchase options");
    }

    /** Policy data prepared for use within the write transaction that owns its definition and group locks. */
    public record LockedPolicyContext(Long categoryId, List<CategoryAttributePolicy> categoryPolicies,
                                      Map<UUID, AttributeDefinition> definitionsById,
                                      Map<OptionGroupId, OptionGroup> catalogOptionGroupsById) {
        public LockedPolicyContext {
            categoryPolicies = List.copyOf(categoryPolicies);
            definitionsById = Map.copyOf(definitionsById);
            catalogOptionGroupsById = Map.copyOf(catalogOptionGroupsById);
        }
    }

    private record ValidationContext(List<CategoryAttributePolicy> categoryPolicies,
                                     Map<UUID, CategoryAttributePolicy> policiesByAttributeId,
                                     Map<UUID, AttributeDefinition> definitionsById,
                                     Map<OptionGroupId, OptionGroup> catalogOptionGroupsById,
                                     Map<UUID, AttributeValue> canonicalValuesById) {
    }
}
