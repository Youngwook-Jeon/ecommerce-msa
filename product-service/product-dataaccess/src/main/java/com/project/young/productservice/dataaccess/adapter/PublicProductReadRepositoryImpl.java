package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.condition.PublicProductSearchCondition;
import com.project.young.productservice.application.dto.query.PublicProductSort;
import com.project.young.productservice.application.dto.result.PublicProductListPageResult;
import com.project.young.productservice.application.port.output.PublicProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.application.port.output.view.ReadProductImageView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionValueView;
import com.project.young.productservice.application.port.output.view.ReadProductVariantView;
import com.project.young.productservice.application.port.output.view.ReadPublicProductSummaryView;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.entity.ProductImageEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueImageEntity;
import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
import com.project.young.productservice.dataaccess.entity.VariantOptionValueEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductImageRoleEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.projection.PublicProductListProjection;
import com.project.young.productservice.dataaccess.repository.ProductImageJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductOptionValueImageJpaRepository;
import com.project.young.productservice.dataaccess.repository.PublicProductSearchQueryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
public class PublicProductReadRepositoryImpl implements PublicProductReadRepository {
    private static final List<ProductStatusEntity> EXCLUDED_STOREFRONT_STATUSES =
            List.of(ProductStatusEntity.DRAFT, ProductStatusEntity.DELETED);

    private final PublicProductSearchQueryRepository publicProductSearchQueryRepository;
    private final ProductJpaRepository productJpaRepository;
    private final ProductDataAccessMapper productDataAccessMapper;
    private final ProductImageJpaRepository productImageJpaRepository;
    private final ProductOptionValueImageJpaRepository productOptionValueImageJpaRepository;

    public PublicProductReadRepositoryImpl(PublicProductSearchQueryRepository publicProductSearchQueryRepository,
                                           ProductJpaRepository productJpaRepository,
                                           ProductDataAccessMapper productDataAccessMapper,
                                           ProductImageJpaRepository productImageJpaRepository,
                                           ProductOptionValueImageJpaRepository productOptionValueImageJpaRepository) {
        this.publicProductSearchQueryRepository = publicProductSearchQueryRepository;
        this.productJpaRepository = productJpaRepository;
        this.productDataAccessMapper = productDataAccessMapper;
        this.productImageJpaRepository = productImageJpaRepository;
        this.productOptionValueImageJpaRepository = productOptionValueImageJpaRepository;
    }

    @Override
    public PublicProductListPageResult search(
            PublicProductSearchCondition condition,
            PublicProductSort sort,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<PublicProductListProjection> rowPage =
                publicProductSearchQueryRepository.search(condition, sort, pageable);

        List<ReadPublicProductSummaryView> content = rowPage.getContent().stream()
                .map(this::toReadPublicProductSummaryView)
                .toList();

        return new PublicProductListPageResult(
                content,
                rowPage.getNumber(),
                rowPage.getSize(),
                rowPage.getTotalElements(),
                rowPage.getTotalPages()
        );
    }

    private ReadPublicProductSummaryView toReadPublicProductSummaryView(PublicProductListProjection row) {
        return ReadPublicProductSummaryView.builder()
                .id(row.id())
                .categoryId(row.categoryId())
                .name(row.name())
                .brand(row.brand())
                .mainImageUrl(row.mainImageUrl())
                .basePrice(row.basePrice())
                .build();
    }

    @Override
    public Optional<ReadProductDetailView> findStorefrontProductDetailById(ProductId productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null.");
        }

        Optional<ProductEntity> optionLoaded = fetchStorefrontDetailRoot(productId.getValue());
        if (optionLoaded.isEmpty()) {
            return Optional.empty();
        }

        hydrateStorefrontVariants(productId.getValue());

        return optionLoaded.map(this::toReadProductDetailView);
    }

    private Optional<ProductEntity> fetchStorefrontDetailRoot(UUID productId) {
        return productJpaRepository.findStorefrontDetailWithOptionsById(
                productId,
                EXCLUDED_STOREFRONT_STATUSES,
                CategoryStatusEntity.DELETED
        );
    }

    private void hydrateStorefrontVariants(UUID productId) {
        // Two-step fetch to avoid cartesian explosion between option/variant collections.
        productJpaRepository.findStorefrontDetailWithVariantsById(
                productId,
                EXCLUDED_STOREFRONT_STATUSES,
                CategoryStatusEntity.DELETED
        );
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
        List<UUID> selectedOptionIds = entity.getSelectedOptionValues() == null
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

    private record DetailImageBundle(
            List<ReadProductImageView> productImages,
            Map<UUID, List<ReadProductImageView>> optionValueImagesById
    ) {
    }
}
