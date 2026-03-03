package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.*;
import com.project.young.productservice.application.mapper.ProductDataMapper;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.domain.repository.ProductRepository;
import com.project.young.productservice.domain.service.ProductDomainService;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class ProductApplicationService {

    private final ProductRepository productRepository;
    private final ProductDomainService productDomainService;
    private final ProductDataMapper productDataMapper;

    public ProductApplicationService(ProductRepository productRepository,
                                     ProductDomainService productDomainService,
                                     ProductDataMapper productDataMapper) {
        this.productRepository = productRepository;
        this.productDomainService = productDomainService;
        this.productDataMapper = productDataMapper;
    }

    @Transactional
    public CreateProductResult createProduct(CreateProductCommand command) {
        validateCreateRequest(command);
        log.info("Attempting to create product with name: {}", command.getName());

        CategoryId categoryId = Optional.ofNullable(command.getCategoryId())
                .map(CategoryId::new)
                .orElse(null);

        productDomainService.validateCategoryForProduct(categoryId);
        Product newProduct = productDataMapper.toProduct(command, categoryId);
        Product savedProduct = persistProduct(newProduct);

        log.info("Product saved successfully with id: {}", savedProduct.getId().getValue());

        return productDataMapper.toCreateProductResult(savedProduct);
    }

    @Transactional
    public UpdateProductResult updateProduct(UUID productIdValue, UpdateProductCommand command) {
        validateUpdateRequest(productIdValue, command);

        ProductId productId = new ProductId(productIdValue);
        log.info("Attempting to update product with id: {}", productId.getValue());

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product with id " + productId.getValue() + " not found."));

        validateProductCanBeUpdated(product);

        boolean isModified = false;

        isModified |= applyCategoryChange(product, command.getCategoryId());
        isModified |= applyNameChange(product, command.getName());
        isModified |= applyDescriptionChange(product, command.getDescription());
        isModified |= applyBasePriceChange(product, command.getBasePrice());
        isModified |= applyBrandChange(product, command.getBrand());
        isModified |= applyMainImageUrlChange(product, command.getMainImageUrl());
        isModified |= applyStatusChange(product, command.getStatus());

        if (isModified) {
            productRepository.save(product);
            log.info("Product updated successfully. id: {}", product.getId().getValue());
        }

        return productDataMapper.toUpdateProductResult(product);
    }

    @Transactional
    public DeleteProductResult deleteProduct(UUID productIdValue) {
        validateDeleteRequest(productIdValue);

        ProductId productId = new ProductId(productIdValue);
        log.info("Attempting to soft-delete product with id: {}", productId.getValue());

        Product deletedProduct = productDomainService.prepareForDeletion(productId);

        log.info("Product deleted successfully. id: {}", deletedProduct.getId().getValue());

        return productDataMapper.toDeleteProductResult(deletedProduct);
    }

    private void validateCreateRequest(CreateProductCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Create product command cannot be null");
        }
    }

    private void validateUpdateRequest(UUID productIdValue, UpdateProductCommand command) {
        if (productIdValue == null || command == null) {
            throw new IllegalArgumentException("Invalid product update request.");
        }
    }

    private void validateDeleteRequest(UUID productIdValue) {
        if (productIdValue == null) {
            log.warn("Attempted to delete product with a null ID value.");
            throw new IllegalArgumentException("Product ID for delete cannot be null.");
        }
    }

    private void validateProductCanBeUpdated(Product product) {
        if (product.isDeleted()) {
            throw new ProductDomainException("Cannot update a product that has been deleted.");
        }
    }

    private Product persistProduct(Product product) {
        Product savedProduct = productRepository.save(product);
        if (savedProduct.getId() == null) {
            log.error("Product ID was not assigned after save for name: {}", savedProduct.getName());
            throw new ProductDomainException("Failed to assign ID to the new product.");
        }
        return savedProduct;
    }

    private boolean applyCategoryChange(Product product, Long newCategoryIdValue) {
        CategoryId currentCategoryId = product.getCategoryId().orElse(null);
        CategoryId newCategoryId = (newCategoryIdValue != null) ? new CategoryId(newCategoryIdValue) : null;

        if (!Objects.equals(currentCategoryId, newCategoryId)) {
            productDomainService.validateCategoryForProduct(newCategoryId);
            product.changeCategoryId(newCategoryId);
            return true;
        }
        return false;
    }

    private boolean applyNameChange(Product product, String newName) {
        if (newName != null && !Objects.equals(product.getName(), newName)) {
            product.changeName(newName);
            return true;
        }
        return false;
    }

    private boolean applyDescriptionChange(Product product, String newDescription) {
        if (newDescription != null && !Objects.equals(product.getDescription(), newDescription)) {
            product.changeDescription(newDescription);
            return true;
        }
        return false;
    }

    private boolean applyBasePriceChange(Product product, BigDecimal newPriceValue) {
        if (newPriceValue != null) {
            Money newBasePrice = new Money(newPriceValue);
            if (!Objects.equals(product.getBasePrice(), newBasePrice)) {
                product.changeBasePrice(newBasePrice);
                return true;
            }
        }
        return false;
    }

    private boolean applyBrandChange(Product product, String newBrand) {
        if (newBrand != null && !Objects.equals(product.getBrand(), newBrand)) {
            product.changeBrand(newBrand);
            return true;
        }
        return false;
    }

    private boolean applyMainImageUrlChange(Product product, String newUrl) {
        if (newUrl != null && !Objects.equals(product.getMainImageUrl(), newUrl)) {
            product.changeMainImageUrl(newUrl);
            return true;
        }
        return false;
    }

    private boolean applyStatusChange(Product product, ProductStatus newStatus) {
        if (newStatus != null && product.getStatus() != newStatus) {
            productDomainService.validateStatusChangeRules(product, newStatus);
            product.changeStatus(newStatus);
            return true;
        }
        return false;
    }
}