package com.project.young.productservice.domain.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.domain.repository.CategoryRepository;
import com.project.young.productservice.domain.repository.ProductRepository;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProductDomainServiceImpl implements ProductDomainService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductDomainServiceImpl(ProductRepository productRepository,
                                    CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void validateCategoryForProduct(CategoryId categoryId) {
        if (categoryId == null) {
            return; // 카테고리 없는 상품 허용
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new ProductDomainException("Category with id " + categoryId.getValue() + " not found.")
                );

        CategoryStatus status = category.getStatus();
        if (status != null && status.isDeleted()) {
            throw new ProductDomainException("Product cannot be assigned to a DELETED category.");
        }
    }

    @Override
    public void validateStatusChangeRules(Product product, ProductStatus newStatus) {
        if (product == null) {
            throw new ProductDomainException("Product must not be null.");
        }
        if (newStatus == null) {
            throw new ProductDomainException("New product status must not be null.");
        }

        if (product.isDeleted()) {
            throw new ProductDomainException(
                    "Cannot change status of a deleted product: " + product.getId().getValue()
            );
        }

        ProductStatus currentStatus = product.getStatus();
        if (currentStatus == newStatus) {
            return; // no-op
        }

        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new ProductDomainException(
                    String.format("Invalid status transition from %s to %s for product %s",
                            currentStatus, newStatus, product.getId().getValue())
            );
        }
    }

    @Override
    public void validateGlobalSkuUniqueness(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new ProductDomainException("SKU cannot be empty.");
        }

        boolean exists = productRepository.existsBySku(sku);
        if (exists) {
            throw new ProductDomainException("Global SKU uniqueness violation. SKU already exists: " + sku);
        }
    }

    @Override
    public Product prepareForDeletion(ProductId productId) {
        if (productId == null) {
            throw new ProductDomainException("Product id must not be null.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ProductNotFoundException("Product with id " + productId.getValue() + " not found.")
                );

        if (product.isDeleted()) {
            log.debug("Product {} is already deleted. Skipping deletion preparation.", productId.getValue());
            return product;
        }

        // TODO: 실제 비즈니스 규칙 추가
        // - 활성 주문에 포함된 상품이면 삭제 불가
        // - 재고/프로모션 등 연관 리소스 체크
        log.debug("Validating deletion rules for product {}", productId.getValue());

        product.markAsDeleted();
        Product saved = productRepository.save(product);

        log.info("Prepared product {} for deletion (soft delete).", productId.getValue());
        return saved;
    }
}