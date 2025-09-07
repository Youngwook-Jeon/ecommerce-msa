package com.project.young.productservice.domain.service;

import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.productservice.domain.entity.Brand;

import java.util.List;

public interface BrandDomainService {

    boolean isBrandNameUnique(String name);

    boolean isBrandNameUniqueForUpdate(String name, BrandId brandIdToExclude);

    /**
     * Validates all business rules for brand creation
     * @param brand The brand to validate
     */
    void validateBrandForCreation(Brand brand);

    /**
     * Validates all business rules for brand update
     * @param brand The existing brand
     * @param newName The new name (can be null if not changing)
     */
    void validateBrandForUpdate(Brand brand, String newName);

    /**
     * Validates status change rules for brands
     * @param brands The brands to validate
     * @param newStatus The new status to apply
     */
    void validateStatusChangeRules(List<Brand> brands, String newStatus);

    /**
     * Processes status change for brands (entity state change only)
     * @param brands The brands to update
     * @param newStatus The new status to apply
     */
    void processStatusChange(List<Brand> brands, String newStatus);

    /**
     * Prepares brands for deletion by validating rules and marking as deleted
     * @param brandIds The IDs of the brands to delete
     * @return A list of all brands that were marked for deletion
     */
    List<Brand> prepareBrandsForDeletion(List<BrandId> brandIds);
}