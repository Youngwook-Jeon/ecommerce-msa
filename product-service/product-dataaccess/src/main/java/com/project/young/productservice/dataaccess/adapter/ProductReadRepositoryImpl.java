package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.port.output.ProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionValueView;
import com.project.young.productservice.application.port.output.view.ReadProductVariantView;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.dataaccess.entity.ProductOptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
import com.project.young.productservice.dataaccess.entity.VariantOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
@Slf4j
public class ProductReadRepositoryImpl implements ProductReadRepository {

    private final ProductJpaRepository productJpaRepository;
    private final ProductDataAccessMapper productDataAccessMapper;

    public ProductReadRepositoryImpl(ProductJpaRepository productJpaRepository,
                                     ProductDataAccessMapper productDataAccessMapper) {
        this.productJpaRepository = productJpaRepository;
        this.productDataAccessMapper = productDataAccessMapper;
    }

    @Override
    public List<ReadProductView> findAllVisibleProducts() {
        return productJpaRepository.findAllVisible(ProductStatusEntity.ACTIVE, CategoryStatusEntity.ACTIVE)
                .stream()
                .map(this::toReadProductView)
                .toList();
    }

    @Override
    public List<ReadProductView> findVisibleByCategoryId(CategoryId categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId must not be null.");
        }

        List<ProductEntity> entities = productJpaRepository.findVisibleByCategoryId(
                categoryId.getValue(),
                ProductStatusEntity.ACTIVE,
                CategoryStatusEntity.ACTIVE
        );
        log.info("Found {} products for category {}", entities.size(), categoryId);

        return entities.stream()
                .map(this::toReadProductView)
                .toList();
    }

    @Override
    public Optional<ReadProductDetailView> findVisibleProductDetailById(ProductId productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null.");
        }

        Optional<ProductEntity> optionLoaded = productJpaRepository.findVisibleDetailWithOptionsById(
                productId.getValue(),
                ProductStatusEntity.ACTIVE,
                CategoryStatusEntity.ACTIVE
        );

        if (optionLoaded.isEmpty()) {
            return Optional.empty();
        }

        // Two-step fetch to avoid large cartesian explosion when loading both bag-like trees.
        productJpaRepository.findVisibleDetailWithVariantsById(
                productId.getValue(),
                ProductStatusEntity.ACTIVE,
                CategoryStatusEntity.ACTIVE
        );

        return optionLoaded.map(this::toReadProductDetailView);
    }

    private ReadProductView toReadProductView(ProductEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null.");

        Long categoryId = (entity.getCategory() != null)
                ? entity.getCategory().getId()
                : null;

        return ReadProductView.builder()
                .id(entity.getId())
                .categoryId(categoryId)
                .name(entity.getName())
                .description(entity.getDescription())
                .brand(entity.getBrand())
                .mainImageUrl(entity.getMainImageUrl())
                .basePrice(entity.getBasePrice())
                .status(productDataAccessMapper.toDomainStatus(entity.getStatus()))
                .conditionType(productDataAccessMapper.toDomainConditionType(entity.getConditionType()))
                .build();
    }

    private ReadProductDetailView toReadProductDetailView(ProductEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null.");

        Long categoryId = (entity.getCategory() != null)
                ? entity.getCategory().getId()
                : null;

        List<ReadProductOptionGroupView> optionGroups = entity.getOptionGroups() == null
                ? List.of()
                : entity.getOptionGroups().stream()
                .map(this::toReadProductOptionGroupView)
                .toList();

        List<ReadProductVariantView> variants = entity.getVariants() == null
                ? List.of()
                : entity.getVariants().stream()
                .map(this::toReadProductVariantView)
                .toList();

        return ReadProductDetailView.builder()
                .id(entity.getId())
                .categoryId(categoryId)
                .name(entity.getName())
                .description(entity.getDescription())
                .brand(entity.getBrand())
                .mainImageUrl(entity.getMainImageUrl())
                .basePrice(entity.getBasePrice())
                .status(productDataAccessMapper.toDomainStatus(entity.getStatus()))
                .conditionType(productDataAccessMapper.toDomainConditionType(entity.getConditionType()))
                .optionGroups(optionGroups)
                .variants(variants)
                .build();
    }

    private ReadProductOptionGroupView toReadProductOptionGroupView(ProductOptionGroupEntity entity) {
        List<ReadProductOptionValueView> optionValues = entity.getOptionValues() == null
                ? List.of()
                : entity.getOptionValues().stream()
                .map(this::toReadProductOptionValueView)
                .toList();

        return ReadProductOptionGroupView.builder()
                .productOptionGroupId(entity.getId())
                .optionGroupId(entity.getOptionGroupId())
                .stepOrder(entity.getStepOrder())
                .required(entity.isRequired())
                .optionValues(optionValues)
                .build();
    }

    private ReadProductOptionValueView toReadProductOptionValueView(ProductOptionValueEntity entity) {
        return ReadProductOptionValueView.builder()
                .productOptionValueId(entity.getId())
                .optionValueId(entity.getOptionValueId())
                .priceDelta(entity.getPriceDelta())
                .isDefault(entity.isDefault())
                .isActive(entity.isActive())
                .build();
    }

    private ReadProductVariantView toReadProductVariantView(ProductVariantEntity entity) {
        List<java.util.UUID> selectedOptionIds = entity.getSelectedOptionValues() == null
                ? List.of()
                : entity.getSelectedOptionValues().stream()
                .map(VariantOptionValueEntity::getProductOptionValueId)
                .toList();

        return ReadProductVariantView.builder()
                .productVariantId(entity.getId())
                .sku(entity.getSku())
                .stockQuantity(entity.getStockQuantity())
                .status(productDataAccessMapper.toDomainStatus(entity.getStatus()))
                .calculatedPrice(entity.getCalculatedPrice())
                .selectedProductOptionValueIds(selectedOptionIds)
                .build();
    }

}