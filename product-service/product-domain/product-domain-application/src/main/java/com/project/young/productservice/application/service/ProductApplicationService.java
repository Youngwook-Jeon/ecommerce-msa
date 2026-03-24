package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.*;
import com.project.young.productservice.application.dto.*;
import com.project.young.productservice.application.mapper.ProductDataMapper;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.entity.ProductOptionGroup;
import com.project.young.productservice.domain.entity.ProductOptionValue;
import com.project.young.productservice.domain.entity.ProductVariant;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import com.project.young.productservice.domain.repository.ProductRepository;
import com.project.young.productservice.domain.service.ProductDomainService;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProductApplicationService {
    private static final int MAX_SKU_GENERATION_RETRIES = 5;

    private final ProductRepository productRepository;
    private final ProductDomainService productDomainService;
    private final ProductDataMapper productDataMapper;
    private final IdGenerator idGenerator;

    public ProductApplicationService(ProductRepository productRepository,
                                     ProductDomainService productDomainService,
                                     ProductDataMapper productDataMapper,
                                     IdGenerator idGenerator) {
        this.productRepository = productRepository;
        this.productDomainService = productDomainService;
        this.productDataMapper = productDataMapper;
        this.idGenerator = idGenerator;
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

        Product product = findProductOrThrow(productId);
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

    @Transactional
    public AddProductOptionGroupResult addProductOptionGroup(UUID productIdValue, AddProductOptionGroupCommand command) {
        if (productIdValue == null || command == null) {
            throw new IllegalArgumentException("Invalid add product option group request.");
        }

        Product product = findProductOrThrow(new ProductId(productIdValue));
        validateProductCanBeUpdated(product);

        ProductOptionGroupId productOptionGroupId = new ProductOptionGroupId(idGenerator.generateId());
        List<ProductOptionValue> optionValues = mapProductOptionValues(command.getOptionValues());

        ProductOptionGroup optionGroup = productDataMapper.toProductOptionGroup(command, productOptionGroupId, optionValues);

        product.addOptionGroup(optionGroup);
        productRepository.save(product);

        return productDataMapper.toAddProductOptionGroupResult(product, optionGroup);
    }

    @Transactional
    public AddProductVariantResult addProductVariant(UUID productIdValue, AddProductVariantCommand command) {
        if (productIdValue == null || command == null) {
            throw new IllegalArgumentException("Invalid add product variant request.");
        }

        Product product = findProductOrThrow(new ProductId(productIdValue));
        validateProductCanBeUpdated(product);

        VariantIdentity identity = generateUniqueVariantIdentity(product.getId());
        ProductVariantId productVariantId = identity.variantId();
        String generatedSku = identity.sku();

        Set<ProductOptionValueId> selectedOptionValueIds = command.getSelectedProductOptionValueIds().stream()
                .map(ProductOptionValueId::new)
                .collect(Collectors.toSet());

        ProductVariant variant = productDataMapper.toProductVariant(command, productVariantId, generatedSku, selectedOptionValueIds);

        product.addVariant(variant);
        productRepository.save(product);

        return productDataMapper.toAddProductVariantResult(product, variant);
    }

    @Transactional
    public ChangeProductOptionValuePriceDeltaResult changeProductOptionValuePriceDelta(
            UUID productIdValue,
            UUID productOptionValueIdValue,
            ChangeProductOptionValuePriceDeltaCommand command
    ) {
        if (productIdValue == null || productOptionValueIdValue == null || command == null) {
            throw new IllegalArgumentException("Invalid product option value price delta change request.");
        }

        Product product = findProductOrThrow(new ProductId(productIdValue));
        validateProductCanBeUpdated(product);

        ProductOptionValueId optionValueId = new ProductOptionValueId(productOptionValueIdValue);
        Money newPriceDelta = new Money(command.getPriceDelta());

        product.changeOptionValuePriceDelta(optionValueId, newPriceDelta);
        productRepository.save(product);

        return productDataMapper.toChangeProductOptionValuePriceDeltaResult(product, optionValueId, newPriceDelta);
    }

    @Transactional
    public UpdateProductVariantResult updateProductVariant(
            UUID productIdValue,
            UUID productVariantIdValue,
            UpdateProductVariantCommand command
    ) {
        if (productIdValue == null || productVariantIdValue == null || command == null) {
            throw new IllegalArgumentException("Invalid product variant update request.");
        }

        Product product = findProductOrThrow(new ProductId(productIdValue));
        validateProductCanBeUpdated(product);

        ProductVariantId variantId = new ProductVariantId(productVariantIdValue);
        ProductVariant updated = product.updateVariantDetails(variantId, command.getStockQuantity(), command.getStatus());
        productRepository.save(product);

        return productDataMapper.toUpdateProductVariantResult(product, updated);
    }

    @Transactional
    public DeleteProductVariantResult deleteProductVariant(UUID productIdValue, UUID productVariantIdValue) {
        if (productIdValue == null || productVariantIdValue == null) {
            throw new IllegalArgumentException("Invalid product variant delete request.");
        }

        Product product = findProductOrThrow(new ProductId(productIdValue));
        validateProductCanBeUpdated(product);

        ProductVariant deleted = product.deleteVariant(new ProductVariantId(productVariantIdValue));
        productRepository.save(product);

        return productDataMapper.toDeleteProductVariantResult(product, deleted);
    }

    @Transactional
    public DeactivateProductOptionValueResult deactivateProductOptionValue(UUID productIdValue, UUID productOptionValueIdValue) {
        if (productIdValue == null || productOptionValueIdValue == null) {
            throw new IllegalArgumentException("Invalid product option value deactivate request.");
        }

        Product product = findProductOrThrow(new ProductId(productIdValue));
        validateProductCanBeUpdated(product);

        ProductOptionValue deactivated = product.deactivateProductOptionValue(new ProductOptionValueId(productOptionValueIdValue));
        productRepository.save(product);

        return productDataMapper.toDeactivateProductOptionValueResult(product, deactivated);
    }

    private List<ProductOptionValue> mapProductOptionValues(List<AddProductOptionValueCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("Product option values must not be empty.");
        }

        return commands.stream()
                .map(this::toProductOptionValue)
                .toList();
    }

    private ProductOptionValue toProductOptionValue(AddProductOptionValueCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Product option value command must not be null.");
        }

        return productDataMapper.toProductOptionValue(command, new ProductOptionValueId(idGenerator.generateId()));
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

    private Product findProductOrThrow(ProductId productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product with id " + productId.getValue() + " not found."));
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

    private VariantIdentity generateUniqueVariantIdentity(ProductId productId) {
        for (int attempt = 1; attempt <= MAX_SKU_GENERATION_RETRIES; attempt++) {
            ProductVariantId candidateVariantId = new ProductVariantId(idGenerator.generateId());
            String candidateSku = generateVariantSku(productId, candidateVariantId);
            try {
                productDomainService.validateGlobalSkuUniqueness(candidateSku);
                return new VariantIdentity(candidateVariantId, candidateSku);
            } catch (ProductDomainException ex) {
                if (attempt == MAX_SKU_GENERATION_RETRIES) {
                    throw new ProductDomainException(
                            "Failed to generate unique SKU after " + MAX_SKU_GENERATION_RETRIES + " attempts.",
                            ex
                    );
                }
                log.warn("SKU collision detected. retry={}/{}", attempt, MAX_SKU_GENERATION_RETRIES);
            }
        }
        throw new ProductDomainException("Unexpected SKU generation flow.");
    }

    private String generateVariantSku(ProductId productId, ProductVariantId variantId) {
        return "PRD-" + shortToken(productId.getValue()) + "-VAR-" + shortToken(variantId.getValue());
    }

    private String shortToken(UUID value) {
        String compact = value.toString().replace("-", "").toUpperCase(Locale.ROOT);
        return compact.substring(0, 8);
    }

    private record VariantIdentity(ProductVariantId variantId, String sku) {
    }
}
