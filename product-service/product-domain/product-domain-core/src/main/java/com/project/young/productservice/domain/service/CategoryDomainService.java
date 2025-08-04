package com.project.young.productservice.domain.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.domain.entity.Category;

import java.util.List;

public interface CategoryDomainService {

    boolean isCategoryNameUnique(String name);

    boolean isParentDepthLessThanLimit(CategoryId parent);

    boolean isCategoryNameUniqueForUpdate(String name, CategoryId categoryIdToExclude);

    /**
     * Validates if a parent category is in a valid state to have a new child.
     * Throws CategoryDomainException if the parent is not valid (e.g., not found or not ACTIVE).
     * @param parentId The ID of the parent category to validate.
     */
    Category validateParentCategory(CategoryId parentId);

    /**
     * Validates parent change rules including existence, depth limit, and circular reference
     * @param categoryId The category being moved
     * @param newParentId The new parent ID (can be null for root)
     */
    void validateParentChangeRules(CategoryId categoryId, CategoryId newParentId);

    /**
     * Validates status change rules for multiple categories
     * @param categories The categories to validate
     * @param newStatus The new status to apply
     */
    void validateStatusChangeRules(List<Category> categories, String newStatus);

    /**
     * Prepares a list of categories for deletion by validating business rules
     * and marking the category and its descendants as DELETED.
     * @param categoryId The ID of the category to delete.
     * @return A list of all categories that were marked for deletion.
     */
    List<Category> prepareForDeletion(CategoryId categoryId);
}