package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.dto.query.AdminProductDetailQuery;
import com.project.young.productservice.application.dto.result.AdminProductDetailResult;
import com.project.young.productservice.application.dto.condition.AdminProductSearchCondition;
import com.project.young.productservice.application.port.output.AdminProductReadRepository;
import com.project.young.productservice.application.port.output.view.ReadProductOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionValueView;
import com.project.young.productservice.application.port.output.view.ReadProductVariantView;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.dataaccess.entity.ProductOptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
import com.project.young.productservice.dataaccess.entity.VariantOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.projection.AdminProductListProjection;
import com.project.young.productservice.dataaccess.repository.AdminProductJpaRepository;
import com.project.young.productservice.dataaccess.repository.AdminProductSearchQueryRepository;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class AdminProductReadRepositoryImpl implements AdminProductReadRepository {

    private final AdminProductJpaRepository adminProductJpaRepository;
    private final AdminProductSearchQueryRepository adminProductSearchQueryRepository;
    private final ProductDataAccessMapper productDataAccessMapper;

    public AdminProductReadRepositoryImpl(AdminProductJpaRepository adminProductJpaRepository,
                                          AdminProductSearchQueryRepository adminProductSearchQueryRepository,
                                          ProductDataAccessMapper productDataAccessMapper) {
        this.adminProductJpaRepository = adminProductJpaRepository;
        this.adminProductSearchQueryRepository = adminProductSearchQueryRepository;
        this.productDataAccessMapper = productDataAccessMapper;
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

        return optionLoaded.map(this::toAdminReadProductDetailView).orElseThrow();
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

    private AdminProductDetailResult toAdminReadProductDetailView(ProductEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null.");

        Long categoryId = entity.getCategory() != null ? entity.getCategory().getId() : null;

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
                .build();
    }

    private ReadProductOptionGroupView toReadProductOptionGroupView(ProductOptionGroupEntity groupEntity) {
        List<ReadProductOptionValueView> optionValues = groupEntity.getOptionValues() == null
                ? List.of()
                : groupEntity.getOptionValues().stream()
                .map(this::toReadProductOptionValueView)
                .toList();

        return ReadProductOptionGroupView.builder()
                .productOptionGroupId(groupEntity.getId())
                .optionGroupId(groupEntity.getOptionGroupId())
                .stepOrder(groupEntity.getStepOrder())
                .required(groupEntity.isRequired())
                .optionValues(optionValues)
                .build();
    }

    private ReadProductOptionValueView toReadProductOptionValueView(ProductOptionValueEntity valueEntity) {
        return ReadProductOptionValueView.builder()
                .productOptionValueId(valueEntity.getId())
                .optionValueId(valueEntity.getOptionValueId())
                .priceDelta(valueEntity.getPriceDelta())
                .isDefault(valueEntity.isDefault())
                .isActive(valueEntity.isActive())
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
                .selectedProductOptionValueIds(selectedOptionIds)
                .build();
    }
}

