package com.project.young.productservice.domain.service;

import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.domain.entity.Brand;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.entity.Product;

import java.util.List;

public interface ProductDomainService {

    boolean isProductNameUnique(String name);

    boolean isProductNameUniqueForUpdate(String name, ProductId productIdToExclude);

    /**
     * Validates if a category is in a valid state to have products assigned
     */
    Category validateCategoryForProduct(CategoryId categoryId);

    /**
     * Validates if a brand is in a valid state to have products assigned
     */
    Brand validateBrandForProduct(BrandId brandId);

    /**
     * Validates all business rules for product creation
     */
    void validateProductForCreation(Product product);

    /**
     * Validates all business rules for product update
     */
    void validateProductForUpdate(Product product, String newName, CategoryId newCategoryId, BrandId newBrandId);

    /**
     * Validates status change rules for products
     */
    void validateStatusChangeRules(List<Product> products, String newStatus);

    /**
     * Processes status change for products (entity state change only)
     */
    void processStatusChange(List<Product> products, String newStatus);

    /**
     * Prepares products for deletion by validating rules and marking as deleted
     */
    List<Product> prepareProductsForDeletion(List<ProductId> productIds);

    /**
     * Handles category status change by updating related products
     */
    void handleCategoryStatusChanged(CategoryId categoryId, String oldStatus, String newStatus);

    /**
     * Handles brand status change by updating related products
     */
    void handleBrandStatusChanged(BrandId brandId, String oldStatus, String newStatus);

    /**
     * Handles category deletion by nullifying category references
     */
    void handleCategoryDeleted(List<CategoryId> categoryIds);

    /**
     * Handles brand deletion by nullifying brand references
     */
    void handleBrandDeleted(BrandId brandId);
}