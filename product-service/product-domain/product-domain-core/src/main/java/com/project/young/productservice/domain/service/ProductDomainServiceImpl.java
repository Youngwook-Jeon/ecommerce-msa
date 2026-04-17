package com.project.young.productservice.domain.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.entity.OptionValue;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.OptionDomainException;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.repository.CategoryRepository;
import com.project.young.productservice.domain.repository.OptionGroupRepository;
import com.project.young.productservice.domain.repository.ProductRepository;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class ProductDomainServiceImpl implements ProductDomainService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OptionGroupRepository optionGroupRepository;

    public ProductDomainServiceImpl(ProductRepository productRepository,
                                    CategoryRepository categoryRepository,
                                    OptionGroupRepository optionGroupRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.optionGroupRepository = optionGroupRepository;
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
    public void validateDeletionRules(Product product) {
        if (product == null) {
            throw new ProductDomainException("Product must not be null.");
        }
        if (product.isDeleted()) {
            return;
        }
        // TODO: 실제 비즈니스 규칙 추가
        // - 활성 주문에 포함된 상품이면 삭제 불가
        // - 재고/프로모션 등 연관 리소스 체크
        log.debug("Validating deletion rules for product {}", product.getId().getValue());
    }

    @Override
    public void validateOptionValueBelongsToGroup(OptionGroupId optionGroupId, OptionValueId optionValueId) {
        validateOptionValuesBelongToGroup(optionGroupId, Set.of(optionValueId));
    }

    @Override
    public void validateOptionValuesBelongToGroup(OptionGroupId optionGroupId, Set<OptionValueId> optionValueIds) {
        if (optionGroupId == null) {
            throw new ProductDomainException("Option group id and option value id must not be null.");
        }
        if (optionValueIds == null || optionValueIds.isEmpty()) {
            throw new ProductDomainException("Option value ids must not be null or empty.");
        }

        OptionGroup optionGroup = optionGroupRepository.findById(optionGroupId)
                .orElseThrow(() ->
                        new ProductDomainException("Global option group not found: " + optionGroupId.getValue()));
        if (optionGroup.getStatus() == null || optionGroup.getStatus().isDeleted()) {
            throw new ProductDomainException("Global option group is not allowed for product composition: " + optionGroupId.getValue());
        }

        Map<OptionValueId, OptionStatus> optionValueStatusById = optionGroup.getOptionValues().stream()
                .collect(Collectors.toMap(OptionValue::getId, OptionValue::getStatus, (left, right) -> left));

        for (OptionValueId optionValueId : optionValueIds) {
            if (optionValueId == null) {
                throw new ProductDomainException("Option value ids must not contain null.");
            }
            OptionStatus optionStatus = optionValueStatusById.get(optionValueId);
            if (optionStatus == null) {
                throw new ProductDomainException(
                        "Option value " + optionValueId.getValue() + " does not belong to option group " + optionGroupId.getValue(),
                        new OptionDomainException("Option value not found in this group.")
                );
            }
            if (optionStatus.isDeleted()) {
                throw new ProductDomainException(
                        "Global option value is not allowed for product composition: " + optionValueId.getValue()
                );
            }
        }
    }
}