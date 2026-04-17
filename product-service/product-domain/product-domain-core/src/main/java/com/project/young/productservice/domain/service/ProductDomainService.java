package com.project.young.productservice.domain.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.valueobject.ProductStatus;

import java.util.Set;

public interface ProductDomainService {
    /**
     * Validates if a category is in a valid state to be assigned to a product.
     * Throws a domain exception if the category is not valid
     * (e.g., not DELETED).
     *
     * @param categoryId category to validate (may be null if product has no category)
     */
    void validateCategoryForProduct(CategoryId categoryId);

    /**
     * Validates status change rules for a product.
     * For example,
     * - enforce allowed transitions defined by ProductStatus.canTransitionTo(...)
     *
     * Throws a domain exception if the change is not allowed.
     *
     * @param product   the product to validate
     * @param newStatus the new status to apply
     */
    void validateStatusChangeRules(Product product, ProductStatus newStatus);

    /**
     * Validates the global uniqueness for a SKU.
     * Ensures that the SKU is not already in use by another product.
     * Throws a domain exception if the SKU is not unique.
     *
     * @param sku the SKU to validate
     */
    void validateGlobalSkuUniqueness(String sku);

    /**
     * Validates deletion rules for a product.
     * Throws a domain exception if the product cannot be deleted.
     *
     * @param product product to validate for deletion
     */
    void validateDeletionRules(Product product);

    /**
     * Validates that a global option value belongs to the given global option group.
     *
     * @param optionGroupId global option group id
     * @param optionValueId global option value id
     */
    void validateOptionValueBelongsToGroup(OptionGroupId optionGroupId, OptionValueId optionValueId);

    /**
     * Validates that all global option values belong to the given global option group.
     *
     * @param optionGroupId  global option group id
     * @param optionValueIds global option value ids
     */
    void validateOptionValuesBelongToGroup(OptionGroupId optionGroupId, Set<OptionValueId> optionValueIds);
}