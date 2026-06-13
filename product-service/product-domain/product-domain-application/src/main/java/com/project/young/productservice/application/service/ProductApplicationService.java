package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.*;
import com.project.young.productservice.application.dto.command.*;
import com.project.young.productservice.application.dto.event.ProductCatalogChangeType;
import com.project.young.productservice.application.dto.result.*;
import com.project.young.productservice.application.mapper.ProductDataMapper;
import com.project.young.productservice.application.port.output.IdGenerator;
import com.project.young.productservice.application.port.output.VariantMainImageSyncPort;
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
    private final VariantMainImageSyncPort variantMainImageSyncPort;
    private final StorefrontProductCatalogInvalidationService storefrontProductCatalogInvalidationService;

    public ProductApplicationService(ProductRepository productRepository,
                                     ProductDomainService productDomainService,
                                     ProductDataMapper productDataMapper,
                                     IdGenerator idGenerator,
                                     VariantMainImageSyncPort variantMainImageSyncPort,
                                     StorefrontProductCatalogInvalidationService storefrontProductCatalogInvalidationService) {
        this.productRepository = productRepository;
        this.productDomainService = productDomainService;
        this.productDataMapper = productDataMapper;
        this.idGenerator = idGenerator;
        this.variantMainImageSyncPort = variantMainImageSyncPort;
        this.storefrontProductCatalogInvalidationService = storefrontProductCatalogInvalidationService;
    }

    @Transactional
    public CreateProductResult createProduct(CreateProductCommand command) {
        validateCreateRequest(command);
        log.info("Attempting to create product with name: {}", command.getName());

        CategoryId categoryId = Optional.ofNullable(command.getCategoryId())
                .map(CategoryId::new)
                .orElse(null);

        productDomainService.validateCategoryForProduct(categoryId);
        ProductId productId = new ProductId(idGenerator.generateId());
        Product newProduct = productDataMapper.toDraftProduct(command, categoryId, productId);
        productRepository.insert(newProduct);

        log.info("Product saved successfully with id: {}", newProduct.getId().getValue());

        return productDataMapper.toCreateProductResult(newProduct);
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

        if (isModified) {
            productRepository.update(product);
            invalidateStorefrontCatalog(product, ProductCatalogChangeType.PRODUCT_UPDATED);
            log.info("Product updated successfully. id: {}", product.getId().getValue());
        }

        return productDataMapper.toUpdateProductResult(product);
    }

    @Transactional
    public UpdateProductResult updateProductStatus(UUID productIdValue, UpdateProductStatusCommand command) {
        validateUpdateStatusRequest(productIdValue, command);

        ProductId productId = new ProductId(productIdValue);
        log.info("Attempting to update product status. id: {}, status: {}", productId.getValue(), command.getStatus());

        Product product = findProductOrThrow(productId);
        validateProductCanBeUpdated(product);

        if (product.getStatus() == command.getStatus()) {
            log.info("Product is already in {} status. id: {}", command.getStatus(), productId.getValue());
            return productDataMapper.toUpdateProductResult(product);
        }

        if (applyStatusChange(product, command.getStatus())) {
            productRepository.update(product);
            invalidateStorefrontCatalog(product, ProductCatalogChangeType.STATUS_CHANGED);
            log.info("Product status updated. id: {}", product.getId().getValue());
        }

        return productDataMapper.toUpdateProductResult(product);
    }

    @Transactional
    public DeleteProductResult deleteProduct(UUID productIdValue) {
        validateDeleteRequest(productIdValue);

        ProductId productId = new ProductId(productIdValue);
        log.info("Attempting to soft-delete product with id: {}", productId.getValue());

        Product product = findProductOrThrow(productId);
        if (product.isDeleted()) {
            log.info("Product already deleted. id: {}", productId.getValue());
            return productDataMapper.toDeleteProductResult(product);
        }
        productDomainService.validateDeletionRules(product);
        product.markAsDeleted();
        productRepository.update(product);
        invalidateStorefrontCatalog(product, ProductCatalogChangeType.DELETED);

        log.info("Product deleted successfully. id: {}", product.getId().getValue());

        return productDataMapper.toDeleteProductResult(product);
    }

    @Transactional
    public AddProductOptionGroupResult addProductOptionGroup(UUID productIdValue, AddProductOptionGroupCommand command) {
        if (productIdValue == null || command == null) {
            throw new IllegalArgumentException("Invalid add product option group request.");
        }

        Product product = findProductOrThrow(new ProductId(productIdValue));
        validateProductCanBeUpdated(product);
        validateOptionGroupStructureMutable(product);

        ProductOptionGroupId productOptionGroupId = new ProductOptionGroupId(idGenerator.generateId());
        OptionGroupId globalOptionGroupId = new OptionGroupId(command.getOptionGroupId());
        validateGlobalOptionValueMembership(globalOptionGroupId, command.getOptionValues());
        List<ProductOptionValue> optionValues = mapProductOptionValues(command.getOptionValues());
        double resolvedStepOrder = command.getStepOrder() == null
                ? resolveStepOrder(product, StepOrderPlacement.append(), null)
                : resolveStepOrder(product, StepOrderPlacement.absolute(command.getStepOrder()), null);

        ProductOptionGroup optionGroup = productDataMapper.toProductOptionGroup(
                command,
                productOptionGroupId,
                resolvedStepOrder,
                optionValues
        );

        product.addOptionGroup(optionGroup);
        productRepository.update(product);
        invalidateStorefrontCatalog(product, ProductCatalogChangeType.OPTION_CHANGED);

        return productDataMapper.toAddProductOptionGroupResult(product, optionGroup);
    }

    @Transactional
    public List<AddProductOptionValueToGroupResult> addProductOptionValues(
            UUID productIdValue,
            UUID productOptionGroupIdValue,
            AddProductOptionValuesCommand command
    ) {
        if (productIdValue == null || productOptionGroupIdValue == null || command == null) {
            throw new IllegalArgumentException("Invalid add product option value request.");
        }
        if (command.getOptionValues() == null || command.getOptionValues().isEmpty()) {
            throw new IllegalArgumentException("Product option values must not be empty.");
        }

        Product product = findProductOrThrow(new ProductId(productIdValue));
        validateProductCanBeUpdated(product);

        ProductOptionGroupId productOptionGroupId = new ProductOptionGroupId(productOptionGroupIdValue);
        ProductOptionGroup targetGroup = product.getOptionGroups().stream()
                .filter(group -> group.getId().equals(productOptionGroupId))
                .findFirst()
                .orElseThrow(() -> new ProductDomainException("Product option group not found in this product."));
        List<AddProductOptionValueToGroupResult> results = new ArrayList<>();
        validateGlobalOptionValueMembership(targetGroup.getOptionGroupId(), command.getOptionValues());
        for (AddProductOptionValueCommand valueCommand : command.getOptionValues()) {
            if (valueCommand == null) {
                throw new IllegalArgumentException("Product option value command must not be null.");
            }
            ProductOptionValue newValue = toProductOptionValue(valueCommand);
            product.addProductOptionValue(productOptionGroupId, newValue);
            results.add(productDataMapper.toAddProductOptionValueToGroupResult(product, productOptionGroupId, newValue));
        }

        productRepository.update(product);
        invalidateStorefrontCatalog(product, ProductCatalogChangeType.OPTION_CHANGED);

        return results;
    }

    @Transactional
    public DeleteProductOptionGroupResult deleteProductOptionGroup(
            UUID productIdValue,
            UUID productOptionGroupIdValue
    ) {
        if (productIdValue == null || productOptionGroupIdValue == null) {
            throw new IllegalArgumentException("Invalid product option group deactivate request.");
        }

        Product product = findProductOrThrow(new ProductId(productIdValue));
        validateProductCanBeUpdated(product);
        validateOptionGroupStructureMutable(product);

        ProductOptionGroup deleted = product.deleteProductOptionGroup(
                new ProductOptionGroupId(productOptionGroupIdValue)
        );
        productRepository.update(product);
        invalidateStorefrontCatalog(product, ProductCatalogChangeType.OPTION_CHANGED);

        return productDataMapper.toDeleteProductOptionGroupResult(product, deleted);
    }

    @Transactional
    public List<AddProductVariantResult> addProductVariants(
            UUID productIdValue,
            AddProductVariantsCommand command
    ) {
        if (productIdValue == null || command == null) {
            throw new IllegalArgumentException("Invalid add product variants request.");
        }

        if (command.getVariants() == null || command.getVariants().isEmpty()) {
            throw new IllegalArgumentException("At least one variant is required.");
        }

        Product product = findProductOrThrow(new ProductId(productIdValue));
        validateProductCanBeUpdated(product);

        List<AddProductVariantResult> results = new ArrayList<>();
        Set<String> reservedSkus = product.getVariants() == null
                ? new HashSet<>()
                : product.getVariants().stream()
                .map(ProductVariant::getSku)
                .collect(Collectors.toSet());
        Set<String> existingActiveCombinationKeys = product.getVariants() == null
                ? new HashSet<>()
                : product.getVariants().stream()
                .filter(variant -> !variant.isDeleted())
                .map(variant -> toVariantCombinationKey(variant.getSelectedOptionValues()))
                .collect(Collectors.toSet());
        Set<String> requestedCombinationKeys = new HashSet<>();

        for (AddProductVariantCommand variantCommand : command.getVariants()) {
            if (variantCommand == null) {
                throw new IllegalArgumentException("Variant command must not be null.");
            }

            Set<ProductOptionValueId> selectedOptionValueIds = variantCommand.getSelectedProductOptionValueIds().stream()
                    .map(ProductOptionValueId::new)
                    .collect(Collectors.toSet());
            String combinationKey = toVariantCombinationKey(selectedOptionValueIds);
            if (existingActiveCombinationKeys.contains(combinationKey) || !requestedCombinationKeys.add(combinationKey)) {
                throw new ProductDomainException("Duplicate variant option combination is not allowed.");
            }

            VariantIdentity identity = generateUniqueVariantIdentity(product.getId(), reservedSkus);
            ProductVariantId productVariantId = identity.variantId();
            String generatedSku = identity.sku();
            reservedSkus.add(generatedSku);

            ProductVariant variant = productDataMapper.toProductVariant(
                    variantCommand,
                    productVariantId,
                    generatedSku,
                    selectedOptionValueIds
            );

            product.addVariant(variant);
            results.add(productDataMapper.toAddProductVariantResult(product, variant));
        }

        productRepository.update(product);

        for (AddProductVariantResult result : results) {
            variantMainImageSyncPort.syncForVariant(result.productVariantId());
        }
        invalidateStorefrontCatalog(product, ProductCatalogChangeType.VARIANT_CHANGED);

        return results;
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
        productRepository.update(product);
        invalidateStorefrontCatalog(product, ProductCatalogChangeType.OPTION_CHANGED);

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
        if (command.getStatus() == ProductStatus.DELETED) {
            throw new ProductDomainException("Use delete variant endpoint for DELETED status.");
        }

        Product product = findProductOrThrow(new ProductId(productIdValue));
        validateProductCanBeUpdated(product);

        ProductVariantId variantId = new ProductVariantId(productVariantIdValue);
        ProductVariant updated = product.updateVariantDetails(variantId, command.getStockQuantity(), command.getStatus());
        productRepository.update(product);
        invalidateStorefrontCatalog(product, ProductCatalogChangeType.VARIANT_CHANGED);

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
        productRepository.update(product);
        invalidateStorefrontCatalog(product, ProductCatalogChangeType.VARIANT_CHANGED);

        return productDataMapper.toDeleteProductVariantResult(product, deleted);
    }

    @Transactional
    public ChangeProductOptionGroupStepOrderResult changeProductOptionGroupStepOrder(
            UUID productIdValue,
            UUID productOptionGroupIdValue,
            ChangeProductOptionGroupStepOrderCommand command
    ) {
        if (productIdValue == null || productOptionGroupIdValue == null || command == null) {
            throw new IllegalArgumentException("Invalid product option group step order change request.");
        }

        Product product = findProductOrThrow(new ProductId(productIdValue));
        validateProductCanBeUpdated(product);
        validateOptionGroupStructureMutable(product);

        ProductOptionGroupId groupId = new ProductOptionGroupId(productOptionGroupIdValue);
        ensureOptionGroupIsActive(product, groupId);
        StepOrderPlacement placement = StepOrderPlacement.fromCommand(command);
        double resolvedStepOrder = resolveStepOrder(product, placement, groupId);
        product.changeOptionGroupStepOrder(groupId, resolvedStepOrder);
        productRepository.update(product);
        invalidateStorefrontCatalog(product, ProductCatalogChangeType.OPTION_CHANGED);

        ProductOptionGroup updated = product.getOptionGroups().stream()
                .filter(group -> group.getId().equals(groupId))
                .findFirst()
                .orElseThrow(() -> new ProductDomainException("Product option group not found in this product."));
        return productDataMapper.toChangeProductOptionGroupStepOrderResult(product, updated);
    }

    @Transactional
    public ReorderProductOptionGroupsResult reorderProductOptionGroups(
            UUID productIdValue,
            ReorderProductOptionGroupsCommand command
    ) {
        if (productIdValue == null || command == null
                || command.getOrderedProductOptionGroupIds() == null
                || command.getOrderedProductOptionGroupIds().isEmpty()) {
            throw new IllegalArgumentException("Invalid product option group reorder request.");
        }

        Product product = findProductOrThrow(new ProductId(productIdValue));
        validateProductCanBeUpdated(product);
        validateOptionGroupStructureMutable(product);

        final double spacing = 1024.0d;
        List<UUID> orderedIds = command.getOrderedProductOptionGroupIds();
        validateReorderTargetsMatchActiveGroups(product, orderedIds);
        for (int i = 0; i < orderedIds.size(); i++) {
            UUID orderedId = orderedIds.get(i);
            product.changeOptionGroupStepOrder(
                    new ProductOptionGroupId(orderedId),
                    (i + 1) * spacing
            );
        }
        productRepository.update(product);
        invalidateStorefrontCatalog(product, ProductCatalogChangeType.OPTION_CHANGED);
        return ReorderProductOptionGroupsResult.builder()
                .productId(product.getId().getValue())
                .updatedCount(orderedIds.size())
                .build();
    }

    @Transactional
    public DeleteProductOptionValueResult deleteProductOptionValue(UUID productIdValue, UUID productOptionValueIdValue) {
        if (productIdValue == null || productOptionValueIdValue == null) {
            throw new IllegalArgumentException("Invalid product option value deactivate request.");
        }

        Product product = findProductOrThrow(new ProductId(productIdValue));
        validateProductCanBeUpdated(product);
        validateOptionGroupStructureMutable(product);

        ProductOptionValue deleted = product.deleteProductOptionValue(new ProductOptionValueId(productOptionValueIdValue));
        productRepository.update(product);
        invalidateStorefrontCatalog(product, ProductCatalogChangeType.OPTION_CHANGED);

        return productDataMapper.toDeleteProductOptionValueResult(product, deleted);
    }

    private void invalidateStorefrontCatalog(Product product, ProductCatalogChangeType changeType) {
        storefrontProductCatalogInvalidationService.invalidate(product, changeType);
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

    private void validateGlobalOptionValueMembership(OptionGroupId optionGroupId, List<AddProductOptionValueCommand> optionValueCommands) {
        if (optionGroupId == null || optionValueCommands == null) {
            return;
        }
        Set<OptionValueId> optionValueIds = optionValueCommands.stream()
                .filter(Objects::nonNull)
                .map(AddProductOptionValueCommand::getOptionValueId)
                .filter(Objects::nonNull)
                .map(OptionValueId::new)
                .collect(Collectors.toSet());
        if (optionValueIds.isEmpty()) {
            return;
        }
        productDomainService.validateOptionValuesBelongToGroup(optionGroupId, optionValueIds);
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

    private void validateUpdateStatusRequest(UUID productIdValue, UpdateProductStatusCommand command) {
        if (productIdValue == null || command == null) {
            throw new IllegalArgumentException("Invalid product status update request.");
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
            if (newStatus == ProductStatus.ACTIVE) {
                validateProductPublishable(product);
            }
            productDomainService.validateStatusChangeRules(product, newStatus);
            product.changeStatus(newStatus);
            return true;
        }
        return false;
    }

    private VariantIdentity generateUniqueVariantIdentity(ProductId productId, Set<String> reservedSkus) {
        for (int attempt = 1; attempt <= MAX_SKU_GENERATION_RETRIES; attempt++) {
            ProductVariantId candidateVariantId = new ProductVariantId(idGenerator.generateId());
            String candidateSku = generateVariantSku(productId, candidateVariantId);
            try {
                if (reservedSkus != null && reservedSkus.contains(candidateSku)) {
                    throw new ProductDomainException("In-request SKU collision: " + candidateSku);
                }
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
        return "PRD-" + shortToken(productId.getValue()) + "-VAR-" + fullToken(variantId.getValue());
    }

    private String toVariantCombinationKey(Set<ProductOptionValueId> selectedOptionValueIds) {
        if (selectedOptionValueIds == null || selectedOptionValueIds.isEmpty()) {
            return "";
        }
        return selectedOptionValueIds.stream()
                .map(ProductOptionValueId::getValue)
                .map(UUID::toString)
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private String shortToken(UUID value) {
        String compact = value.toString().replace("-", "").toUpperCase(Locale.ROOT);
        return compact.substring(0, 8);
    }

    private String fullToken(UUID value) {
        return value.toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private void validateProductPublishable(Product product) {
        boolean hasActiveVariant = product.getVariants() != null
                && product.getVariants().stream().anyMatch(variant -> variant.getStatus() == ProductStatus.ACTIVE);
        if (!hasActiveVariant) {
            throw new ProductDomainException("Cannot publish product without at least one active variant.");
        }
    }

    private void validateOptionGroupStructureMutable(Product product) {
        if (product.getStatus() != ProductStatus.DRAFT) {
            throw new ProductDomainException("Option group/value changes are allowed only when product is DRAFT.");
        }
    }

    private List<ProductOptionGroup> getActiveOptionGroups(Product product) {
        return product.getOptionGroups().stream()
                .filter(group -> group.getStatus() == null || !group.getStatus().isDeleted())
                .toList();
    }

    private void ensureOptionGroupIsActive(Product product, ProductOptionGroupId targetGroupId) {
        boolean exists = getActiveOptionGroups(product).stream()
                .anyMatch(group -> group.getId().equals(targetGroupId));
        if (!exists) {
            throw new ProductDomainException("Active product option group not found in this product.");
        }
    }

    private void validateReorderTargetsMatchActiveGroups(Product product, List<UUID> orderedIds) {
        Set<UUID> activeIds = getActiveOptionGroups(product).stream()
                .map(group -> group.getId().getValue())
                .collect(Collectors.toSet());
        Set<UUID> requestedIds = new HashSet<>(orderedIds);

        if (requestedIds.size() != orderedIds.size()) {
            throw new ProductDomainException("Reorder request contains duplicated product option group ids.");
        }
        if (!requestedIds.equals(activeIds)) {
            throw new ProductDomainException(
                    "Reorder request must include all and only non-deleted product option groups."
            );
        }
    }

    private double resolveStepOrder(
            Product product,
            StepOrderPlacement placement,
            ProductOptionGroupId excludeGroupId
    ) {
        final double spacing = 1024.0d;
        final double minGap = 0.000001d;

        List<ProductOptionGroup> activeGroups = getActiveOptionGroups(product).stream()
                .filter(group -> excludeGroupId == null || !group.getId().equals(excludeGroupId))
                .sorted(Comparator.comparingDouble(ProductOptionGroup::getStepOrder))
                .toList();

        if (placement.mode() == StepOrderPlacementMode.APPEND) {
            if (activeGroups.isEmpty()) {
                return spacing;
            }
            return activeGroups.getLast().getStepOrder() + spacing;
        }

        if (placement.mode() == StepOrderPlacementMode.PREPEND) {
            if (activeGroups.isEmpty()) {
                return spacing;
            }
            double first = activeGroups.getFirst().getStepOrder();
            if (first > spacing) {
                return first - spacing;
            }
            return first / 2.0d;
        }

        if (placement.mode() == StepOrderPlacementMode.BEFORE || placement.mode() == StepOrderPlacementMode.AFTER) {
            if (placement.anchorProductOptionGroupId() == null) {
                throw new ProductDomainException("Anchor product option group id is required for BEFORE/AFTER placement.");
            }

            ProductOptionGroupId anchorId = new ProductOptionGroupId(placement.anchorProductOptionGroupId());
            int anchorIndex = -1;
            for (int i = 0; i < activeGroups.size(); i++) {
                if (activeGroups.get(i).getId().equals(anchorId)) {
                    anchorIndex = i;
                    break;
                }
            }
            if (anchorIndex < 0) {
                throw new ProductDomainException("Anchor product option group not found in this product.");
            }

            if (placement.mode() == StepOrderPlacementMode.BEFORE) {
                ProductOptionGroup anchor = activeGroups.get(anchorIndex);
                Double prev = anchorIndex > 0 ? activeGroups.get(anchorIndex - 1).getStepOrder() : null;
                if (prev == null) {
                    return Math.max(anchor.getStepOrder() / 2.0d, minGap);
                }
                if ((anchor.getStepOrder() - prev) < minGap) {
                    product.rebalanceOptionGroupStepOrders();
                    return resolveStepOrder(product, placement, excludeGroupId);
                }
                return (prev + anchor.getStepOrder()) / 2.0d;
            }

            ProductOptionGroup anchor = activeGroups.get(anchorIndex);
            Double next = anchorIndex < activeGroups.size() - 1
                    ? activeGroups.get(anchorIndex + 1).getStepOrder()
                    : null;
            if (next == null) {
                return anchor.getStepOrder() + spacing;
            }
            if ((next - anchor.getStepOrder()) < minGap) {
                product.rebalanceOptionGroupStepOrders();
                return resolveStepOrder(product, placement, excludeGroupId);
            }
            return (anchor.getStepOrder() + next) / 2.0d;
        }

        Double requestedStepOrder = placement.absoluteStepOrder();
        if (requestedStepOrder == null) {
            throw new ProductDomainException("Step order is required for ABSOLUTE placement.");
        }
        if (requestedStepOrder <= 0) {
            throw new ProductDomainException("Step order must be greater than zero.");
        }

        boolean duplicate = activeGroups.stream()
                .anyMatch(group -> Double.compare(group.getStepOrder(), requestedStepOrder) == 0);
        if (!duplicate) {
            return requestedStepOrder;
        }

        double prev = 0.0d;
        Double next = null;
        for (ProductOptionGroup group : activeGroups) {
            if (group.getStepOrder() <= requestedStepOrder) {
                prev = group.getStepOrder();
                continue;
            }
            next = group.getStepOrder();
            break;
        }

        if (next == null) {
            return requestedStepOrder + spacing;
        }

        double candidate = (prev + next) / 2.0d;
        if ((next - prev) < minGap) {
            product.rebalanceOptionGroupStepOrders();
            activeGroups = getActiveOptionGroups(product).stream()
                    .filter(group -> excludeGroupId == null || !group.getId().equals(excludeGroupId))
                    .sorted(Comparator.comparingDouble(ProductOptionGroup::getStepOrder))
                    .toList();

            prev = 0.0d;
            next = null;
            for (ProductOptionGroup group : activeGroups) {
                if (group.getStepOrder() <= requestedStepOrder) {
                    prev = group.getStepOrder();
                    continue;
                }
                next = group.getStepOrder();
                break;
            }
            if (next == null) {
                return activeGroups.getLast().getStepOrder() + spacing;
            }
            candidate = (prev + next) / 2.0d;
        }
        return candidate;
    }

    private record VariantIdentity(ProductVariantId variantId, String sku) {

    }

    private enum StepOrderPlacementMode {
        APPEND,
        PREPEND,
        BEFORE,
        AFTER,
        ABSOLUTE
    }

    private record StepOrderPlacement(
            StepOrderPlacementMode mode,
            Double absoluteStepOrder,
            UUID anchorProductOptionGroupId
    ) {
        static StepOrderPlacement append() {
            return new StepOrderPlacement(StepOrderPlacementMode.APPEND, null, null);
        }

        static StepOrderPlacement absolute(Double stepOrder) {
            return new StepOrderPlacement(StepOrderPlacementMode.ABSOLUTE, stepOrder, null);
        }

        static StepOrderPlacement prepend() {
            return new StepOrderPlacement(StepOrderPlacementMode.PREPEND, null, null);
        }

        static StepOrderPlacement before(UUID anchorProductOptionGroupId) {
            return new StepOrderPlacement(StepOrderPlacementMode.BEFORE, null, anchorProductOptionGroupId);
        }

        static StepOrderPlacement after(UUID anchorProductOptionGroupId) {
            return new StepOrderPlacement(StepOrderPlacementMode.AFTER, null, anchorProductOptionGroupId);
        }

        static StepOrderPlacement fromCommand(ChangeProductOptionGroupStepOrderCommand command) {
            if (command == null || command.getPlacementMode() == null) {
                throw new ProductDomainException("Placement mode is required.");
            }

            return switch (command.getPlacementMode()) {
                case APPEND -> append();
                case PREPEND -> prepend();
                case BEFORE -> before(command.getAnchorProductOptionGroupId());
                case AFTER -> after(command.getAnchorProductOptionGroupId());
                case ABSOLUTE -> absolute(command.getStepOrder());
            };
        }
    }
}
