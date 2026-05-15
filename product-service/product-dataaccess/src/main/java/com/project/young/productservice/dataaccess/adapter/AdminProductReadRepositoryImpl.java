package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.dto.query.AdminProductDetailQuery;
import com.project.young.productservice.application.dto.result.AdminProductDetailResult;
import com.project.young.productservice.application.dto.condition.AdminProductSearchCondition;
import com.project.young.productservice.application.port.output.AdminProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadProductImageView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionValueView;
import com.project.young.productservice.application.port.output.view.ReadProductVariantView;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.dataaccess.entity.ProductOptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
import com.project.young.productservice.dataaccess.entity.VariantOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.entity.ProductImageEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductImageRoleEntity;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.projection.AdminProductListProjection;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueImageEntity;
import com.project.young.productservice.dataaccess.repository.AdminProductJpaRepository;
import com.project.young.productservice.dataaccess.repository.AdminProductSearchQueryRepository;
import com.project.young.productservice.dataaccess.repository.ProductImageJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductOptionValueImageJpaRepository;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class AdminProductReadRepositoryImpl implements AdminProductReadRepository {

    private final AdminProductJpaRepository adminProductJpaRepository;
    private final AdminProductSearchQueryRepository adminProductSearchQueryRepository;
    private final ProductDataAccessMapper productDataAccessMapper;
    private final ProductImageJpaRepository productImageJpaRepository;
    private final ProductOptionValueImageJpaRepository productOptionValueImageJpaRepository;

    public AdminProductReadRepositoryImpl(AdminProductJpaRepository adminProductJpaRepository,
                                          AdminProductSearchQueryRepository adminProductSearchQueryRepository,
                                          ProductDataAccessMapper productDataAccessMapper,
                                          ProductImageJpaRepository productImageJpaRepository,
                                          ProductOptionValueImageJpaRepository productOptionValueImageJpaRepository) {
        this.adminProductJpaRepository = adminProductJpaRepository;
        this.adminProductSearchQueryRepository = adminProductSearchQueryRepository;
        this.productDataAccessMapper = productDataAccessMapper;
        this.productImageJpaRepository = productImageJpaRepository;
        this.productOptionValueImageJpaRepository = productOptionValueImageJpaRepository;
    }

    @Override
    public AdminProductDetailResult getProductDetail(AdminProductDetailQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("AdminProductDetailQuery cannot be null");
        }
        var optionLoaded = adminProductJpaRepository.findAdminDetailWithOptionsById(query.id());
        if (optionLoaded.isEmpty()) {
            throw new ProductNotFoundException("Product not found: " + query.id());
        }
        adminProductJpaRepository.findAdminDetailWithVariantsById(query.id());

        ProductEntity entity = optionLoaded.orElseThrow();
        List<ReadProductImageView> images = productImageJpaRepository
                .findByProduct_IdAndStatusOrderBySortOrderAsc(entity.getId(), OptionStatusEntity.ACTIVE)
                .stream()
                .sorted(Comparator
                        .comparing((ProductImageEntity e) -> e.getRole() == ProductImageRoleEntity.MAIN ? 0 : 1)
                        .thenComparingInt(ProductImageEntity::getSortOrder))
                .map(this::toReadProductImageView)
                .toList();

        return toAdminReadProductDetailView(entity, images, loadOptionValueImages(entity));
    }

    @Override
    public List<ReadProductVariantView> getProductVariants(AdminProductDetailQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("AdminProductDetailQuery cannot be null");
        }

        var loaded = adminProductJpaRepository.findAdminDetailWithVariantsById(query.id());
        if (loaded.isEmpty()) {
            throw new ProductNotFoundException("Product not found: " + query.id());
        }

        ProductEntity entity = loaded.orElseThrow();
        if (entity.getVariants() == null || entity.getVariants().isEmpty()) {
            return List.of();
        }
        return entity.getVariants().stream()
                .map(this::toReadProductVariantView)
                .toList();
    }

    @Override
    public AdminProductSearchResult search(AdminProductSearchCondition condition,
                                           int page,
                                           int size,
                                           String sortProperty,
                                           boolean ascending) {
        if (condition == null) {
            throw new IllegalArgumentException("AdminProductSearchCondition cannot be null");
        }

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(size, 1),
                Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC,
                        sortProperty != null && !sortProperty.isBlank() ? sortProperty : "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );

        Page<AdminProductListProjection> rowPage = adminProductSearchQueryRepository.search(condition, pageable);

        List<ReadProductView> views = rowPage.getContent().stream()
                .map(this::toReadProductView)
                .toList();

        return new AdminProductSearchResult(
                views,
                rowPage.getNumber(),
                rowPage.getSize(),
                rowPage.getTotalElements(),
                rowPage.getTotalPages()
        );
    }

    private ReadProductView toReadProductView(AdminProductListProjection row) {
        return ReadProductView.builder()
                .id(row.id())
                .categoryId(row.categoryId())
                .name(row.name())
                .description(row.description())
                .brand(row.brand())
                .mainImageUrl(row.mainImageUrl())
                .basePrice(row.basePrice())
                .status(productDataAccessMapper.toDomainStatus(row.status()))
                .conditionType(productDataAccessMapper.toDomainConditionType(row.conditionType()))
                .build();
    }

    private AdminProductDetailResult toAdminReadProductDetailView(
            ProductEntity entity,
            List<ReadProductImageView> images,
            Map<UUID, List<ReadProductImageView>> optionValueImagesById
    ) {
        Objects.requireNonNull(entity, "entity must not be null.");

        Long categoryId = entity.getCategory() != null ? entity.getCategory().getId() : null;

        List<ReadProductOptionGroupView> optionGroups = entity.getOptionGroups() == null
                ? List.of()
                : entity.getOptionGroups().stream()
                .map(group -> toReadProductOptionGroupView(group, optionValueImagesById))
                .toList();

        List<ReadProductVariantView> variants = entity.getVariants() == null
                ? List.of()
                : entity.getVariants().stream()
                .map(this::toReadProductVariantView)
                .toList();

        return AdminProductDetailResult.builder()
                .id(entity.getId())
                .categoryId(categoryId)
                .name(entity.getName())
                .description(entity.getDescription())
                .brand(entity.getBrand())
                .mainImageUrl(entity.getMainImageUrl())
                .basePrice(entity.getBasePrice())
                .status(productDataAccessMapper.toDomainStatus(entity.getStatus()))
                .conditionType(productDataAccessMapper.toDomainConditionType(entity.getConditionType()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .optionGroups(optionGroups)
                .variants(variants)
                .images(images)
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

    private ReadProductOptionGroupView toReadProductOptionGroupView(
            ProductOptionGroupEntity groupEntity,
            Map<UUID, List<ReadProductImageView>> optionValueImagesById
    ) {
        List<ReadProductOptionValueView> optionValues = groupEntity.getOptionValues() == null
                ? List.of()
                : groupEntity.getOptionValues().stream()
                .map(value -> toReadProductOptionValueView(value, optionValueImagesById))
                .toList();

        return ReadProductOptionGroupView.builder()
                .productOptionGroupId(groupEntity.getId())
                .optionGroupId(groupEntity.getOptionGroupId())
                .stepOrder(groupEntity.getStepOrder())
                .required(groupEntity.isRequired())
                .drivesVariantImages(groupEntity.isDrivesVariantImages())
                .status(productDataAccessMapper.toDomainOptionStatus(groupEntity.getStatus()))
                .optionValues(optionValues)
                .build();
    }

    private ReadProductOptionValueView toReadProductOptionValueView(
            ProductOptionValueEntity valueEntity,
            Map<UUID, List<ReadProductImageView>> optionValueImagesById
    ) {
        return ReadProductOptionValueView.builder()
                .productOptionValueId(valueEntity.getId())
                .optionValueId(valueEntity.getOptionValueId())
                .priceDelta(valueEntity.getPriceDelta())
                .isDefault(valueEntity.isDefault())
                .status(productDataAccessMapper.toDomainOptionStatus(valueEntity.getStatus()))
                .images(optionValueImagesById.getOrDefault(valueEntity.getId(), List.of()))
                .build();
    }

    private ReadProductVariantView toReadProductVariantView(ProductVariantEntity variantEntity) {
        List<UUID> selectedOptionIds = variantEntity.getSelectedOptionValues() == null
                ? List.of()
                : variantEntity.getSelectedOptionValues().stream()
                .map(VariantOptionValueEntity::getProductOptionValueId)
                .toList();

        return ReadProductVariantView.builder()
                .productVariantId(variantEntity.getId())
                .sku(variantEntity.getSku())
                .stockQuantity(variantEntity.getStockQuantity())
                .status(productDataAccessMapper.toDomainStatus(variantEntity.getStatus()))
                .calculatedPrice(variantEntity.getCalculatedPrice())
                .mainImageUrl(variantEntity.getMainImageUrl())
                .selectedProductOptionValueIds(selectedOptionIds)
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

    private ReadProductImageView toReadProductImageView(ProductOptionValueImageEntity e) {
        return ReadProductImageView.builder()
                .id(e.getId())
                .publicUrl(e.getPublicUrl())
                .role(e.getRole().name())
                .status(e.getStatus().name())
                .sortOrder(e.getSortOrder())
                .build();
    }
}

