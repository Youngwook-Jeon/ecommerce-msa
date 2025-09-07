package com.project.young.productservice.domain.service;

import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.domain.entity.Brand;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.BrandDomainException;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.repository.BrandRepository;
import com.project.young.productservice.domain.repository.CategoryRepository;
import com.project.young.productservice.domain.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

@Slf4j
public class ProductDomainServiceImpl implements ProductDomainService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public ProductDomainServiceImpl(ProductRepository productRepository,
                                    CategoryRepository categoryRepository,
                                    BrandRepository brandRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
    }

    @Override
    public boolean isProductNameUnique(String name) {
        return !productRepository.existsByName(name);
    }

    @Override
    public boolean isProductNameUniqueForUpdate(String name, ProductId productIdToExclude) {
        return !productRepository.existsByNameAndIdNot(name, productIdToExclude);
    }

    @Override
    public Category validateCategoryForProduct(CategoryId categoryId) {
        if (categoryId == null) {
            return null;
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryDomainException("Category with id " + categoryId.getValue() + " not found"));

        if (category.isDeleted()) {
            throw new CategoryDomainException("Cannot assign product to deleted category");
        }

        if (!Category.STATUS_ACTIVE.equals(category.getStatus())) {
            throw new CategoryDomainException("Cannot assign product to inactive category");
        }

        return category;
    }

    @Override
    public Brand validateBrandForProduct(BrandId brandId) {
        if (brandId == null) {
            return null;
        }

        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new BrandDomainException("Brand with id " + brandId.getValue() + " not found"));

        if (brand.isDeleted()) {
            throw new BrandDomainException("Cannot assign product to deleted brand");
        }

        if (!Brand.STATUS_ACTIVE.equals(brand.getStatus())) {
            throw new BrandDomainException("Cannot assign product to inactive brand");
        }

        return brand;
    }

    @Override
    public void validateProductForCreation(Product product) {
        log.debug("Validating product for creation: {}", product.getName());

        if (!isProductNameUnique(product.getName())) {
            throw new ProductDomainException("Product name '" + product.getName() + "' already exists");
        }

        product.getCategoryId().ifPresent(this::validateCategoryForProduct);
        product.getBrandId().ifPresent(this::validateBrandForProduct);

        log.debug("Product validation passed for: {}", product.getName());
    }

    @Override
    public void validateProductForUpdate(Product product, String newName, CategoryId newCategoryId, BrandId newBrandId) {
        log.debug("Validating product for update: {}", product.getId().getValue());

        if (product.isDeleted()) {
            throw new ProductDomainException("Cannot update deleted product");
        }

        if (newName != null && !newName.equals(product.getName())) {
            if (!isProductNameUniqueForUpdate(newName, product.getId())) {
                throw new ProductDomainException("Product name '" + newName + "' already exists");
            }
        }

        if (newCategoryId != null && !newCategoryId.equals(product.getCategoryId().orElse(null))) {
            validateCategoryForProduct(newCategoryId);
        }

        if (newBrandId != null && !newBrandId.equals(product.getBrandId().orElse(null))) {
            validateBrandForProduct(newBrandId);
        }

        log.debug("Product update validation passed for: {}", product.getId().getValue());
    }

    @Override
    public void validateStatusChangeRules(List<Product> products, String newStatus) {
        if (newStatus == null) {
            return;
        }

        for (Product product : products) {
            if (product.isDeleted()) {
                throw new ProductDomainException("Cannot change status of deleted product: " + product.getId().getValue());
            }

            if (Objects.equals(product.getStatus(), newStatus)) {
                continue;
            }

            if (!isValidStatusTransition(product.getStatus(), newStatus)) {
                throw new ProductDomainException(
                        String.format("Invalid status transition from %s to %s for product %s",
                                product.getStatus(), newStatus, product.getId().getValue())
                );
            }
        }
    }

    @Override
    public void processStatusChange(List<Product> products, String newStatus) {
        log.info("Processing status change for {} products to status: {}", products.size(), newStatus);

        if (products.isEmpty()) {
            throw new IllegalArgumentException("Products list cannot be null or empty");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("New status cannot be null");
        }

        List<Product> productsToUpdate = products.stream()
                .filter(product -> !Objects.equals(product.getStatus(), newStatus))
                .toList();

        if (productsToUpdate.isEmpty()) {
            log.info("No products need status update - all are already in status: {}", newStatus);
            return;
        }

        log.info("Updating status for {} products (filtered from {})",
                productsToUpdate.size(), products.size());

        for (Product product : productsToUpdate) {
            try {
                product.changeStatus(newStatus);
                log.debug("Updated product {} status to {}", product.getId().getValue(), newStatus);
            } catch (ProductDomainException e) {
                log.error("Failed to update product {} status: {}", product.getId().getValue(), e.getMessage());
                throw new ProductDomainException(
                        String.format("Failed to update product %s status: %s",
                                product.getId().getValue(), e.getMessage()), e);
            }
        }

    }

    @Override
    public List<Product> prepareProductsForDeletion(List<ProductId> productIds) {
        log.info("Preparing {} products for deletion", productIds.size());

        List<Product> products = productRepository.findAllById(productIds);

        if (products.size() != productIds.size()) {
            throw new ProductDomainException("Some products not found for deletion");
        }

        validateDeletionRules(products);

        List<Product> productsToDelete = products.stream()
                .filter(product -> !product.isDeleted())
                .toList();

        productsToDelete.forEach(Product::markAsDeleted);

        log.info("Prepared {} products for deletion (skipped {} already deleted)",
                productsToDelete.size(), products.size() - productsToDelete.size());

        return productsToDelete;
    }

    // 이벤트 핸들러 메서드들
    @Override
    public void handleCategoryStatusChanged(CategoryId categoryId, String oldStatus, String newStatus) {
        log.info("Handling category status change: category={}, {} -> {}",
                categoryId.getValue(), oldStatus, newStatus);

        if (Category.STATUS_INACTIVE.equals(newStatus) && Category.STATUS_ACTIVE.equals(oldStatus)) {
            productRepository.updateStatusByCategoryId(Product.STATUS_INACTIVE, categoryId);
            log.info("Products under category {} set to INACTIVE", categoryId.getValue());
        }
    }

    @Override
    public void handleBrandStatusChanged(BrandId brandId, String oldStatus, String newStatus) {
        log.info("Handling brand status change: brand={}, {} -> {}",
                brandId.getValue(), oldStatus, newStatus);

        if (Brand.STATUS_INACTIVE.equals(newStatus) && Brand.STATUS_ACTIVE.equals(oldStatus)) {
            productRepository.updateStatusByBrandId(Product.STATUS_INACTIVE, brandId);
            log.info("Products under brand {} set to INACTIVE", brandId.getValue());
        }
    }

    @Override
    public void handleCategoryDeleted(List<CategoryId> categoryIds) {
        log.info("Handling category deletion for {} categories", categoryIds.size());

        for (CategoryId categoryId : categoryIds) {
            long affectedProducts = productRepository.countByCategoryId(categoryId);
            if (affectedProducts > 0) {
                log.info("Nullifying category reference for {} products", affectedProducts);
                productRepository.nullifyCategoryReference(categoryId);
            }
        }
    }

    @Override
    public void handleBrandDeleted(BrandId brandId) {
        log.info("Handling brand deletion: brand={}", brandId.getValue());

        long affectedProducts = productRepository.countByBrandId(brandId);
        if (affectedProducts > 0) {
            log.info("Nullifying brand reference for {} products", affectedProducts);
            productRepository.nullifyBrandReference(brandId);
        }
    }

    private boolean isValidStatusTransition(String currentStatus, String newStatus) {
        return switch (currentStatus) {
            case Product.STATUS_ACTIVE -> Product.STATUS_INACTIVE.equals(newStatus);
            case Product.STATUS_INACTIVE -> Product.STATUS_ACTIVE.equals(newStatus);
            default -> false;
        };
    }

    private void validateDeletionRules(List<Product> productsToDelete) {
        log.debug("Validating deletion rules for {} products", productsToDelete.size());
        // TODO: 추가 비즈니스 규칙 검증
    }
}