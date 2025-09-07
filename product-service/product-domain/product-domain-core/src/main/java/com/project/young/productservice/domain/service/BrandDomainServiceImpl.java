package com.project.young.productservice.domain.service;

import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.productservice.domain.entity.Brand;
import com.project.young.productservice.domain.exception.BrandDomainException;
import com.project.young.productservice.domain.repository.BrandRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

@Slf4j
public class BrandDomainServiceImpl implements BrandDomainService {

    private final BrandRepository brandRepository;

    public BrandDomainServiceImpl(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    @Override
    public boolean isBrandNameUnique(String name) {
        return !brandRepository.existsByName(name);
    }

    @Override
    public boolean isBrandNameUniqueForUpdate(String name, BrandId brandIdToExclude) {
        return !brandRepository.existsByNameAndIdNot(name, brandIdToExclude);
    }

    @Override
    public void validateBrandForCreation(Brand brand) {
        log.debug("Validating brand for creation: {}", brand.getName());

        if (!isBrandNameUnique(brand.getName())) {
            throw new BrandDomainException("Brand name '" + brand.getName() + "' already exists");
        }

        log.debug("Brand validation passed for: {}", brand.getName());
    }

    @Override
    public void validateBrandForUpdate(Brand brand, String newName) {
        log.debug("Validating brand for update: {}", brand.getId().getValue());

        if (brand.isDeleted()) {
            throw new BrandDomainException("Cannot update deleted brand");
        }

        if (newName != null && !newName.equals(brand.getName())) {
            if (!isBrandNameUniqueForUpdate(newName, brand.getId())) {
                throw new BrandDomainException("Brand name '" + newName + "' already exists");
            }
        }

        log.debug("Brand update validation passed for: {}", brand.getId().getValue());
    }

    @Override
    public void validateStatusChangeRules(List<Brand> brands, String newStatus) {
        if (newStatus == null) {
            return;
        }

        for (Brand brand : brands) {
            if (brand.isDeleted()) {
                throw new BrandDomainException("Cannot change status of deleted brand: " + brand.getId().getValue());
            }

            if (Objects.equals(brand.getStatus(), newStatus)) {
                continue;
            }

            if (!isValidStatusTransition(brand.getStatus(), newStatus)) {
                throw new BrandDomainException(
                        String.format("Invalid status transition from %s to %s for brand %s",
                                brand.getStatus(), newStatus, brand.getId().getValue())
                );
            }
        }
    }

    @Override
    public void processStatusChange(List<Brand> brands, String newStatus) {
        log.info("Processing status change for {} brands to status: {}", brands.size(), newStatus);

        if (brands.isEmpty()) {
            throw new IllegalArgumentException("Brands list cannot be null or empty");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("New status cannot be null");
        }

        List<Brand> brandsToUpdate = brands.stream()
                .filter(brand -> !Objects.equals(brand.getStatus(), newStatus))
                .toList();

        if (brandsToUpdate.isEmpty()) {
            log.info("No brands need status update - all are already in status: {}", newStatus);
            return;
        }

        log.info("Updating status for {} brands (filtered from {})",
                brandsToUpdate.size(), brands.size());

        for (Brand brand : brandsToUpdate) {
            try {
                brand.changeStatus(newStatus);
                log.debug("Updated brand {} status to {}", brand.getId().getValue(), newStatus);
            } catch (BrandDomainException e) {
                log.error("Failed to update brand {} status: {}", brand.getId().getValue(), e.getMessage());
                throw new BrandDomainException(
                        String.format("Failed to update brand %s status: %s",
                                brand.getId().getValue(), e.getMessage()), e);
            }
        }

    }

    @Override
    public List<Brand> prepareBrandsForDeletion(List<BrandId> brandIds) {
        log.info("Preparing {} brands for deletion", brandIds.size());

        List<Brand> brands = brandRepository.findAllById(brandIds);

        if (brands.size() != brandIds.size()) {
            throw new BrandDomainException("Some brands not found for deletion");
        }

        validateDeletionRules(brands);

        List<Brand> brandsToDelete = brands.stream()
                .filter(brand -> !brand.isDeleted())
                .toList();

        brandsToDelete.forEach(Brand::markAsDeleted);

        log.info("Prepared {} brands for deletion (skipped {} already deleted)",
                brandsToDelete.size(), brands.size() - brandsToDelete.size());

        return brandsToDelete;
    }

    private boolean isValidStatusTransition(String currentStatus, String newStatus) {
        return switch (currentStatus) {
            case Brand.STATUS_ACTIVE -> Brand.STATUS_INACTIVE.equals(newStatus);
            case Brand.STATUS_INACTIVE -> Brand.STATUS_ACTIVE.equals(newStatus);
            default -> false;
        };
    }

    private void validateDeletionRules(List<Brand> brandsToDelete) {
        log.debug("Validating deletion rules for {} brands", brandsToDelete.size());
        // TODO: 추가 비즈니스 규칙 검증
    }
}