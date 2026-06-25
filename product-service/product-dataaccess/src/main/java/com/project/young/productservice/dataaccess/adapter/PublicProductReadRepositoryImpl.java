package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.condition.PublicProductSearchCondition;
import com.project.young.productservice.application.dto.query.PublicProductSort;
import com.project.young.productservice.application.dto.result.PublicProductListPageResult;
import com.project.young.productservice.application.port.output.PublicProductReadRepository;
import com.project.young.productservice.application.policy.StorefrontProductVisibilityPolicy;
import com.project.young.productservice.application.port.output.view.ReadCartCatalogLineView;
import com.project.young.productservice.application.port.output.view.ReadCartCatalogOptionLineView;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.application.port.output.view.ReadProductImageView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionValueView;
import com.project.young.productservice.application.port.output.view.ReadProductVariantView;
import com.project.young.productservice.application.port.output.view.ReadPublicProductSummaryView;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
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
import com.project.young.productservice.dataaccess.repository.OptionGroupJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductImageJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductOptionValueImageJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductVariantJpaRepository;
import com.project.young.productservice.dataaccess.repository.PublicProductSearchQueryRepository;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final OptionGroupJpaRepository optionGroupJpaRepository;
    private final ProductVariantJpaRepository productVariantJpaRepository;

    public PublicProductReadRepositoryImpl(PublicProductSearchQueryRepository publicProductSearchQueryRepository,
                                           ProductJpaRepository productJpaRepository,
                                           ProductDataAccessMapper productDataAccessMapper,
                                           ProductImageJpaRepository productImageJpaRepository,
                                           ProductOptionValueImageJpaRepository productOptionValueImageJpaRepository,
                                           OptionGroupJpaRepository optionGroupJpaRepository,
                                           ProductVariantJpaRepository productVariantJpaRepository) {
        this.publicProductSearchQueryRepository = publicProductSearchQueryRepository;
        this.productJpaRepository = productJpaRepository;
        this.productDataAccessMapper = productDataAccessMapper;
        this.productImageJpaRepository = productImageJpaRepository;
        this.productOptionValueImageJpaRepository = productOptionValueImageJpaRepository;
        this.optionGroupJpaRepository = optionGroupJpaRepository;
        this.productVariantJpaRepository = productVariantJpaRepository;
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

    @Override
    public List<ReadCartCatalogLineView> findCartCatalogLinesByVariantIds(List<UUID> productVariantIds) {
        if (productVariantIds == null || productVariantIds.isEmpty()) {
            return List.of();
        }

        List<ProductVariantEntity> variants = productVariantJpaRepository.findStorefrontCartVariantsByIdIn(
                productVariantIds,
                EXCLUDED_STOREFRONT_STATUSES,
                CategoryStatusEntity.DELETED
        );
        if (variants.isEmpty()) {
            return List.of();
        }

        List<UUID> productIds = variants.stream()
                .map(variant -> variant.getProduct().getId())
                .distinct()
                .toList();

        Map<UUID, ProductEntity> productsById = productJpaRepository.findStorefrontOptionsByIdIn(
                        productIds,
                        EXCLUDED_STOREFRONT_STATUSES,
                        CategoryStatusEntity.DELETED
                ).stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        OptionMetadataBundle optionMetadata = loadOptionMetadataByOptionGroupIds(
                collectOptionGroupIds(productsById.values())
        );
        Map<UUID, ProductOptionIndex> optionIndexByProductId = productsById.values().stream()
                .collect(Collectors.toMap(ProductEntity::getId, this::buildProductOptionIndex));

        return variants.stream()
                .map(variant -> toReadCartCatalogLineView(
                        variant,
                        optionMetadata,
                        optionIndexByProductId.get(variant.getProduct().getId())
                ))
                .filter(Objects::nonNull)
                .toList();
    }

    private ReadCartCatalogLineView toReadCartCatalogLineView(
            ProductVariantEntity variant,
            OptionMetadataBundle optionMetadata,
            ProductOptionIndex optionIndex
    ) {
        if (variant == null || variant.getProduct() == null) {
            return null;
        }

        ProductEntity product = variant.getProduct();
        ProductStatus productStatus = productDataAccessMapper.toDomainStatus(product.getStatus());
        ProductStatus variantStatus = productDataAccessMapper.toDomainStatus(variant.getStatus());
        boolean purchasable = StorefrontProductVisibilityPolicy.isPurchasable(productStatus)
                && variantStatus.isActive();

        String imageUrl = variant.getMainImageUrl() != null ? variant.getMainImageUrl() : product.getMainImageUrl();

        return ReadCartCatalogLineView.builder()
                .productId(product.getId())
                .productVariantId(variant.getId())
                .productName(product.getName())
                .brand(product.getBrand())
                .sku(variant.getSku())
                .imageUrl(imageUrl)
                .unitPrice(variant.getCalculatedPrice())
                .purchasable(purchasable)
                .stockQuantity(variant.getStockQuantity())
                .variantOptions(buildVariantOptions(variant, optionMetadata, optionIndex))
                .build();
    }

    private List<ReadCartCatalogOptionLineView> buildVariantOptions(
            ProductVariantEntity variant,
            OptionMetadataBundle optionMetadata,
            ProductOptionIndex optionIndex
    ) {
        if (variant.getSelectedOptionValues() == null || variant.getSelectedOptionValues().isEmpty()) {
            return List.of();
        }
        if (optionIndex == null || optionIndex.isEmpty()) {
            return List.of();
        }

        List<ReadCartCatalogOptionLineView> optionLines = new ArrayList<>();
        for (VariantOptionValueEntity selected : variant.getSelectedOptionValues()) {
            UUID productOptionValueId = selected.getProductOptionValueId();
            ProductOptionGroupEntity group = optionIndex.groupByOptionValueId().get(productOptionValueId);
            ProductOptionValueEntity optionValue = optionIndex.optionValueById().get(productOptionValueId);
            if (group == null || optionValue == null) {
                continue;
            }

            OptionGroupEntity globalGroup = optionMetadata.groupById().get(group.getOptionGroupId());
            String optionGroupName = globalGroup != null ? globalGroup.getDisplayName() : null;
            String optionValueName = optionMetadata.valueDisplayNameById().get(optionValue.getOptionValueId());

            optionLines.add(ReadCartCatalogOptionLineView.builder()
                    .stepOrder((int) group.getStepOrder())
                    .productOptionGroupId(group.getId())
                    .optionGroupName(optionGroupName)
                    .productOptionValueId(productOptionValueId)
                    .optionValueName(optionValueName)
                    .build());
        }

        optionLines.sort(Comparator.comparingInt(ReadCartCatalogOptionLineView::stepOrder));
        return List.copyOf(optionLines);
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
        OptionMetadataBundle optionMetadata = loadOptionMetadata(entity);
        List<ReadProductOptionGroupView> optionGroups = mapOptionGroups(
                entity,
                imageBundle.optionValueImagesById(),
                optionMetadata
        );
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
            Map<UUID, List<ReadProductImageView>> optionValueImagesById,
            OptionMetadataBundle optionMetadata
    ) {
        if (entity.getOptionGroups() == null) {
            return List.of();
        }
        return entity.getOptionGroups().stream()
                .map(group -> toReadProductOptionGroupView(group, optionValueImagesById, optionMetadata))
                .toList();
    }

    private OptionMetadataBundle loadOptionMetadata(ProductEntity entity) {
        return loadOptionMetadataByOptionGroupIds(collectOptionGroupIds(List.of(entity)));
    }

    private List<UUID> collectOptionGroupIds(Collection<ProductEntity> products) {
        return products.stream()
                .filter(product -> product.getOptionGroups() != null)
                .flatMap(product -> product.getOptionGroups().stream())
                .map(ProductOptionGroupEntity::getOptionGroupId)
                .distinct()
                .toList();
    }

    private OptionMetadataBundle loadOptionMetadataByOptionGroupIds(Collection<UUID> optionGroupIds) {
        if (optionGroupIds == null || optionGroupIds.isEmpty()) {
            return OptionMetadataBundle.empty();
        }

        List<UUID> distinctOptionGroupIds = optionGroupIds.stream().distinct().toList();
        if (distinctOptionGroupIds.isEmpty()) {
            return OptionMetadataBundle.empty();
        }

        Map<UUID, OptionGroupEntity> groupById = optionGroupJpaRepository.findAllByIdIn(distinctOptionGroupIds).stream()
                .collect(Collectors.toMap(OptionGroupEntity::getId, Function.identity()));

        Map<UUID, String> valueDisplayNameById = new HashMap<>();
        for (OptionGroupEntity group : groupById.values()) {
            if (group.getOptionValues() == null) {
                continue;
            }
            for (OptionValueEntity value : group.getOptionValues()) {
                valueDisplayNameById.put(value.getId(), value.getDisplayName());
            }
        }

        return new OptionMetadataBundle(groupById, valueDisplayNameById);
    }

    private ProductOptionIndex buildProductOptionIndex(ProductEntity productWithOptions) {
        if (productWithOptions == null || productWithOptions.getOptionGroups() == null) {
            return ProductOptionIndex.empty();
        }

        Map<UUID, ProductOptionValueEntity> optionValueById = new HashMap<>();
        Map<UUID, ProductOptionGroupEntity> groupByOptionValueId = new HashMap<>();
        for (ProductOptionGroupEntity group : productWithOptions.getOptionGroups()) {
            if (group.getOptionValues() == null) {
                continue;
            }
            for (ProductOptionValueEntity optionValue : group.getOptionValues()) {
                optionValueById.put(optionValue.getId(), optionValue);
                groupByOptionValueId.put(optionValue.getId(), group);
            }
        }

        if (optionValueById.isEmpty()) {
            return ProductOptionIndex.empty();
        }
        return new ProductOptionIndex(optionValueById, groupByOptionValueId);
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
            Map<UUID, List<ReadProductImageView>> optionValueImagesById,
            OptionMetadataBundle optionMetadata
    ) {
        List<ReadProductOptionValueView> optionValues = entity.getOptionValues() == null
                ? List.of()
                : entity.getOptionValues().stream()
                .map(value -> toReadProductOptionValueView(value, optionValueImagesById, optionMetadata))
                .toList();

        OptionGroupEntity globalGroup = optionMetadata.groupById().get(entity.getOptionGroupId());

        return ReadProductOptionGroupView.builder()
                .productOptionGroupId(entity.getId())
                .optionGroupId(entity.getOptionGroupId())
                .groupKey(globalGroup != null ? globalGroup.getName() : null)
                .displayName(globalGroup != null ? globalGroup.getDisplayName() : null)
                .stepOrder(entity.getStepOrder())
                .required(entity.isRequired())
                .drivesVariantImages(entity.isDrivesVariantImages())
                .status(productDataAccessMapper.toDomainOptionStatus(entity.getStatus()))
                .optionValues(optionValues)
                .build();
    }

    private ReadProductOptionValueView toReadProductOptionValueView(
            ProductOptionValueEntity entity,
            Map<UUID, List<ReadProductImageView>> optionValueImagesById,
            OptionMetadataBundle optionMetadata
    ) {
        return ReadProductOptionValueView.builder()
                .productOptionValueId(entity.getId())
                .optionValueId(entity.getOptionValueId())
                .displayName(optionMetadata.valueDisplayNameById().get(entity.getOptionValueId()))
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

    private record OptionMetadataBundle(
            Map<UUID, OptionGroupEntity> groupById,
            Map<UUID, String> valueDisplayNameById
    ) {
        private static OptionMetadataBundle empty() {
            return new OptionMetadataBundle(Map.of(), Map.of());
        }
    }

    private record ProductOptionIndex(
            Map<UUID, ProductOptionValueEntity> optionValueById,
            Map<UUID, ProductOptionGroupEntity> groupByOptionValueId
    ) {
        private static ProductOptionIndex empty() {
            return new ProductOptionIndex(Map.of(), Map.of());
        }

        private boolean isEmpty() {
            return optionValueById.isEmpty();
        }
    }
}
