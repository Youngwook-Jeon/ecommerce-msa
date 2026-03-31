package com.project.young.productservice.domain.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.valueobject.CategoryStatus;

import java.util.List;

public interface CategoryDomainService {

    boolean isCategoryNameUnique(String name);

    boolean isParentDepthLessThanLimit(CategoryId parent);

    boolean isCategoryNameUniqueForUpdate(String name, CategoryId categoryIdToExclude);

    void validateParentCategory(Category parentCategory);

    /**
     * Validates parent change rules including validation, depth limit, and circular reference
     * @param categoryId The category being moved
     * @param newParentId The new parent ID (can be null for root)
     */
    void validateParentChangeRules(CategoryId categoryId, CategoryId newParentId, Category newParentCategory);

    /**
     * Validates status change rules for multiple categories
     * @param categories The categories to validate
     * @param newStatus The new status to apply
     */
    void validateStatusChangeRules(List<Category> categories, CategoryStatus newStatus);

    void validateDeletionRules(List<Category> categoriesToDelete);
}