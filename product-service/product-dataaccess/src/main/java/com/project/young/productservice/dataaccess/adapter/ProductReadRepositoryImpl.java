package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.port.output.ProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.application.port.output.view.ReadProductImageView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionValueView;
import com.project.young.productservice.application.port.output.view.ReadProductVariantView;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.dataaccess.entity.ProductOptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueImageEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductImageEntity;
import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
import com.project.young.productservice.dataaccess.entity.VariantOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductImageRoleEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.ProductImageJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductOptionValueImageJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
@Slf4j
public class ProductReadRepositoryImpl implements ProductReadRepository {

    private final ProductJpaRepository productJpaRepository;
    private final ProductDataAccessMapper productDataAccessMapper;
    private final ProductImageJpaRepository productImageJpaRepository;
    private final ProductOptionValueImageJpaRepository productOptionValueImageJpaRepository;

    public ProductReadRepositoryImpl(ProductJpaRepository productJpaRepository,
                                     ProductDataAccessMapper productDataAccessMapper,
                                     ProductImageJpaRepository productImageJpaRepository,
                                     ProductOptionValueImageJpaRepository productOptionValueImageJpaRepository) {
        this.productJpaRepository = productJpaRepository;
        this.productDataAccessMapper = productDataAccessMapper;
        this.productImageJpaRepository = productImageJpaRepository;
        this.productOptionValueImageJpaRepository = productOptionValueImageJpaRepository;
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

    @Override
    public Optional<ReadProductDetailView> findStorefrontProductDetailById(ProductId productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null.");
        }

        List<ProductStatusEntity> excludedStatuses = excludedStorefrontStatuses();

        Optional<ProductEntity> optionLoaded = productJpaRepository.findStorefrontDetailWithOptionsById(
                productId.getValue(),
                excludedStatuses,
                CategoryStatusEntity.DELETED
        );
        if (optionLoaded.isEmpty()) {
            return Optional.empty();
        }

        productJpaRepository.findStorefrontDetailWithVariantsById(
                productId.getValue(),
                excludedStatuses,
                CategoryStatusEntity.DELETED
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

        DetailImageBundle imageBundle = loadDetailImages(entity);
        List<ReadProductOptionGroupView> optionGroups = mapOptionGroups(entity, imageBundle.optionValueImagesById());
        List<ReadProductVariantView> variants = mapVariants(entity);

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
                .images(imageBundle.productImages())
                .optionGroups(optionGroups)
                .variants(variants)
                .build();
    }

    private List<ReadProductOptionGroupView> mapOptionGroups(
            ProductEntity entity,
            Map<UUID, List<ReadProductImageView>> optionValueImagesById
    ) {
        if (entity.getOptionGroups() == null) {
            return List.of();
        }
        return entity.getOptionGroups().stream()
                .map(group -> toReadProductOptionGroupView(group, optionValueImagesById))
                .toList();
    }

    private List<ReadProductVariantView> mapVariants(ProductEntity entity) {
        if (entity.getVariants() == null) {
            return List.of();
        }
        return entity.getVariants().stream()
                .map(this::toReadProductVariantView)
                .toList();
    }

    private DetailImageBundle loadDetailImages(ProductEntity entity) {
        return new DetailImageBundle(
                loadProductImages(entity.getId()),
                loadOptionValueImages(entity)
        );
    }

    private List<ReadProductImageView> loadProductImages(UUID productId) {
        return productImageJpaRepository
                .findByProduct_IdAndStatusOrderBySortOrderAsc(productId, OptionStatusEntity.ACTIVE)
                .stream()
                .sorted(Comparator
                        .comparing((ProductImageEntity e) -> e.getRole() == ProductImageRoleEntity.MAIN ? 0 : 1)
                        .thenComparingInt(ProductImageEntity::getSortOrder))
                .map(this::toReadProductImageView)
                .toList();
    }

    private ReadProductOptionGroupView toReadProductOptionGroupView(
            ProductOptionGroupEntity entity,
            Map<UUID, List<ReadProductImageView>> optionValueImagesById
    ) {
        List<ReadProductOptionValueView> optionValues = entity.getOptionValues() == null
                ? List.of()
                : entity.getOptionValues().stream()
                .map(value -> toReadProductOptionValueView(value, optionValueImagesById))
                .toList();

        return ReadProductOptionGroupView.builder()
                .productOptionGroupId(entity.getId())
                .optionGroupId(entity.getOptionGroupId())
                .stepOrder(entity.getStepOrder())
                .required(entity.isRequired())
                .drivesVariantImages(entity.isDrivesVariantImages())
                .status(productDataAccessMapper.toDomainOptionStatus(entity.getStatus()))
                .optionValues(optionValues)
                .build();
    }

    private ReadProductOptionValueView toReadProductOptionValueView(
            ProductOptionValueEntity entity,
            Map<UUID, List<ReadProductImageView>> optionValueImagesById
    ) {
        return ReadProductOptionValueView.builder()
                .productOptionValueId(entity.getId())
                .optionValueId(entity.getOptionValueId())
                .priceDelta(entity.getPriceDelta())
                .isDefault(entity.isDefault())
                .status(productDataAccessMapper.toDomainOptionStatus(entity.getStatus()))
                .images(optionValueImagesById.getOrDefault(entity.getId(), List.of()))
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
                .mainImageUrl(entity.getMainImageUrl())
                .selectedProductOptionValueIds(selectedOptionIds)
                .build();
    }

    private ReadProductImageView toReadProductImageView(ProductImageEntity e) {
        return ReadProductImageView.builder()
                .id(e.getId())
                .publicUrl(e.getPublicUrl())
                .role(e.getRole().name())
                .status(e.getStatus().name())
                .sortOrder(e.getSortOrder())
                .build();
    }

    private ReadProductImageView toReadProductImageView(ProductOptionValueImageEntity e) {
        return ReadProductImageView.builder()
                .id(e.getId())
                .publicUrl(e.getPublicUrl())
                .role(e.getRole().name())
                .status(e.getStatus().name())
                .sortOrder(e.getSortOrder())
                .build();
    }

    private Map<UUID, List<ReadProductImageView>> loadOptionValueImages(ProductEntity entity) {
        if (entity.getOptionGroups() == null || entity.getOptionGroups().isEmpty()) {
            return Map.of();
        }
        List<UUID> povIds = entity.getOptionGroups().stream()
                .filter(group -> group.getOptionValues() != null)
                .flatMap(group -> group.getOptionValues().stream())
                .map(ProductOptionValueEntity::getId)
                .toList();
        if (povIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<ReadProductImageView>> grouped = new HashMap<>();
        for (UUID povId : povIds) {
            grouped.put(povId, new ArrayList<>());
        }

        productOptionValueImageJpaRepository
                .findByProductOptionValue_IdInAndStatusOrderBySortOrderAsc(povIds, OptionStatusEntity.ACTIVE)
                .stream()
                .sorted(Comparator
                        .comparing((ProductOptionValueImageEntity e) -> e.getRole() == ProductImageRoleEntity.MAIN ? 0 : 1)
                        .thenComparingInt(ProductOptionValueImageEntity::getSortOrder))
                .forEach(image -> grouped
                        .computeIfAbsent(image.getProductOptionValue().getId(), ignored -> new ArrayList<>())
                        .add(toReadProductImageView(image)));

        return grouped;
    }

    /**
     * Storefront PDP allows detail preview for non-listed states (e.g. INACTIVE),
     * but must hide draft/deleted entities.
     */
    private List<ProductStatusEntity> excludedStorefrontStatuses() {
        return List.of(ProductStatusEntity.DRAFT, ProductStatusEntity.DELETED);
    }

    private record DetailImageBundle(
            List<ReadProductImageView> productImages,
            Map<UUID, List<ReadProductImageView>> optionValueImagesById
    ) {
    }

}