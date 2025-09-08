package com.project.young.productservice.application.service;

import com.project.young.common.domain.event.publisher.DomainEventPublisher;
import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.product.*;
import com.project.young.productservice.application.mapper.ProductDataMapper;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.event.ProductStatusChangedEvent;
import com.project.young.productservice.domain.exception.DuplicateProductNameException;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.domain.repository.ProductRepository;
import com.project.young.productservice.domain.service.ProductDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class ProductApplicationService {

    private final ProductRepository productRepository;
    private final ProductDomainService productDomainService;
    private final ProductDataMapper productDataMapper;
    private final DomainEventPublisher domainEventPublisher;

    public ProductApplicationService(ProductRepository productRepository,
                                     ProductDomainService productDomainService,
                                     ProductDataMapper productDataMapper,
                                     DomainEventPublisher domainEventPublisher) {
        this.productRepository = productRepository;
        this.productDomainService = productDomainService;
        this.productDataMapper = productDataMapper;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public CreateProductResponse createProduct(CreateProductCommand command) {
        log.info("Attempting to create product with name: {}", command.getName());

        if (!productDomainService.isProductNameUnique(command.getName())) {
            log.warn("Product name already exists: {}", command.getName());
            throw new DuplicateProductNameException("Product name '" + command.getName() + "' already exists.");
        }

        Product newProduct = productDataMapper.toProduct(command);

        // 도메인 서비스로 생성 검증
        productDomainService.validateProductForCreation(newProduct);

        Product savedProduct = persistProduct(newProduct);

        log.info("Product saved successfully with id: {}", savedProduct.getId().getValue());
        return productDataMapper.toCreateProductResponse(savedProduct,
                "Product " + savedProduct.getName() + " created successfully.");
    }

    @Transactional
    public UpdateProductResponse updateProduct(Long productIdValue, UpdateProductCommand command) {
        validateUpdateRequest(productIdValue);

        ProductId productId = new ProductId(productIdValue);
        log.info("Attempting to update product with id: {}", productId.getValue());

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product with id " + productId.getValue() + " not found."));
        validateProductCanBeUpdated(product);

        // 도메인 서비스로 업데이트 검증
        CategoryId newCategoryId = command.getCategoryId() != null ? new CategoryId(command.getCategoryId()) : null;
        BrandId newBrandId = command.getBrandId() != null ? new BrandId(command.getBrandId()) : null;
        productDomainService.validateProductForUpdate(product, command.getName(), newCategoryId, newBrandId);

        boolean hasChanges = performUpdates(product, command, productId);

        String message;
        if (hasChanges) {
            productRepository.save(product);
            message = "Product '" + product.getName() + "' updated successfully.";
        } else {
            message = "Product '" + product.getName() + "' was not changed.";
        }

        return productDataMapper.toUpdateProductResponse(product, message);
    }

    @Transactional
    public DeleteProductResponse deleteProduct(Long productIdValue) {
        validateDeleteRequest(productIdValue);

        ProductId productId = new ProductId(productIdValue);
        log.info("Attempting to soft-delete product with id: {}", productId.getValue());

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product with id " + productId.getValue() + " not found."));

        if (product.isDeleted()) {
            throw new ProductDomainException("Product is already deleted.");
        }

        String productName = product.getName();
        product.markAsDeleted();
        productRepository.save(product);

        log.info("Product marked as deleted: {}", productName);

        return new DeleteProductResponse(productId.getValue(),
                "Product " + productName + " (ID: " + productId.getValue() + ") marked as deleted successfully.");
    }

    @Transactional
    public void updateProductsStatus(List<ProductId> productIds, String newStatus) {
        log.info("Updating status for {} products to: {}", productIds.size(), newStatus);

        List<Product> products = productRepository.findAllById(productIds);

        if (products.size() != productIds.size()) {
            throw new IllegalArgumentException("Some products not found");
        }

        // 현재 상태 기록
        List<StatusChangeRecord> statusChanges = products.stream()
                .map(product -> new StatusChangeRecord(product.getId(), product.getStatus(), newStatus))
                .filter(record -> !record.oldStatus().equals(record.newStatus()))
                .toList();

        if (statusChanges.isEmpty()) {
            log.info("No products need status update - all are already in status: {}", newStatus);
            return;
        }

        productDomainService.validateStatusChangeRules(products, newStatus);
        productDomainService.processStatusChange(products, newStatus);

        productRepository.saveAll(products);

        // 이벤트 발행
        statusChanges.forEach(change -> {
            ProductStatusChangedEvent event = new ProductStatusChangedEvent(
                    change.productId(), change.oldStatus(), change.newStatus()
            );
            domainEventPublisher.publishEventAfterCommit(event);
        });

        log.info("Successfully updated status for {} products and published {} events",
                products.size(), statusChanges.size());
    }

    private void validateUpdateRequest(Long productIdValue) {
        if (productIdValue == null) {
            throw new IllegalArgumentException("Product ID for update cannot be null.");
        }
    }

    private void validateDeleteRequest(Long productIdValue) {
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

    private boolean performUpdates(Product product, UpdateProductCommand command, ProductId productId) {
        boolean nameChanged = applyNameChange(product, command.getName(), productId);
        boolean descriptionChanged = applyDescriptionChange(product, command.getDescription());
        boolean priceChanged = applyPriceChange(product, command.getBasePrice());
        boolean categoryChanged = applyCategoryChange(product, command.getCategoryId());
        boolean brandChanged = applyBrandChange(product, command.getBrandId());
        boolean conditionChanged = applyConditionChange(product, command.getConditionType());
        boolean statusChanged = applyStatusChange(product, command.getStatus());

        return nameChanged || descriptionChanged || priceChanged ||
                categoryChanged || brandChanged || conditionChanged || statusChanged;
    }

    private boolean applyNameChange(Product product, String newName, ProductId productId) {
        if (newName != null && !Objects.equals(product.getName(), newName)) {
            if (!productDomainService.isProductNameUniqueForUpdate(newName, productId)) {
                throw new DuplicateProductNameException("Product name '" + newName + "' already exists.");
            }
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

    private boolean applyPriceChange(Product product, java.math.BigDecimal newPrice) {
        if (newPrice != null && !Objects.equals(product.getBasePrice(), newPrice)) {
            product.changeBasePrice(newPrice);
            return true;
        }
        return false;
    }

    private boolean applyCategoryChange(Product product, Long newCategoryIdValue) {
        CategoryId newCategoryId = newCategoryIdValue != null ? new CategoryId(newCategoryIdValue) : null;
        CategoryId currentCategoryId = product.getCategoryId().orElse(null);

        if (!Objects.equals(currentCategoryId, newCategoryId)) {
            product.changeCategory(newCategoryId);
            return true;
        }
        return false;
    }

    private boolean applyBrandChange(Product product, Long newBrandIdValue) {
        BrandId newBrandId = newBrandIdValue != null ? new BrandId(newBrandIdValue) : null;
        BrandId currentBrandId = product.getBrandId().orElse(null);

        if (!Objects.equals(currentBrandId, newBrandId)) {
            product.changeBrand(newBrandId);
            return true;
        }
        return false;
    }

    private boolean applyConditionChange(Product product, String newConditionType) {
        if (newConditionType != null && !Objects.equals(product.getConditionType(), newConditionType)) {
            product.changeConditionType(newConditionType);
            return true;
        }
        return false;
    }

    private boolean applyStatusChange(Product product, String newStatus) {
        if (newStatus != null && !Objects.equals(product.getStatus(), newStatus)) {
            String oldStatus = product.getStatus();

            productDomainService.validateStatusChangeRules(List.of(product), newStatus);

            product.changeStatus(newStatus);

            // 상태 변경 이벤트 발행
            ProductStatusChangedEvent event = new ProductStatusChangedEvent(
                    product.getId(), oldStatus, newStatus
            );
            domainEventPublisher.publishEventAfterCommit(event);

            return true;
        }
        return false;
    }

    private Product persistProduct(Product product) {
        Product savedProduct = productRepository.save(product);
        if (savedProduct.getId() == null) {
            log.error("Product ID was not assigned after save for name: {}", savedProduct.getName());
            throw new ProductDomainException("Failed to assign ID to the new product.");
        }
        return savedProduct;
    }

    // 상태 변경 기록용 레코드
    private record StatusChangeRecord(ProductId productId, String oldStatus, String newStatus) {}
}