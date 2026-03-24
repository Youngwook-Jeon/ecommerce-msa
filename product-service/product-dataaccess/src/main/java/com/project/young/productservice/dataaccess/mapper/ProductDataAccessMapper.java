package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.*;
import com.project.young.productservice.dataaccess.entity.*;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.entity.ProductOptionGroup;
import com.project.young.productservice.domain.entity.ProductOptionValue;
import com.project.young.productservice.domain.entity.ProductVariant;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ProductDataAccessMapper {

    public Product productEntityToProduct(ProductEntity productEntity) {
        Objects.requireNonNull(productEntity, "productEntity must not be null.");

        return Product.reconstitute(
                new ProductId(productEntity.getId()),
                toCategoryId(productEntity),
                productEntity.getName(),
                productEntity.getDescription(),
                new Money(productEntity.getBasePrice()),
                toDomainStatus(productEntity.getStatus()),
                toDomainConditionType(productEntity.getConditionType()),
                productEntity.getBrand(),
                productEntity.getMainImageUrl(),
                mapOptionGroupsToDomain(productEntity.getOptionGroups()),
                mapVariantsToDomain(productEntity.getVariants())
        );
    }

    public ProductEntity productToProductEntity(Product product, CategoryEntity categoryEntity) {
        Objects.requireNonNull(product, "product must not be null.");

        ProductEntity entity = ProductEntity.builder()
                .id(product.getId() != null ? product.getId().getValue() : null)
                .category(categoryEntity)
                .name(product.getName())
                .description(product.getDescription())
                .basePrice(product.getBasePrice().getAmount())
                .status(toEntityStatus(product.getStatus()))
                .conditionType(toEntityConditionType(product.getConditionType()))
                .brand(product.getBrand())
                .mainImageUrl(product.getMainImageUrl())
                .optionGroups(new HashSet<>())
                .variants(new HashSet<>())
                .build();

        mergeOptionGroups(product, entity);
        mergeVariants(product, entity);
        return entity;
    }

    public void updateEntityFromDomain(Product domainProduct, ProductEntity productEntity, CategoryEntity categoryEntity) {
        Objects.requireNonNull(domainProduct, "domainProduct must not be null.");
        Objects.requireNonNull(productEntity, "productEntity must not be null.");

        productEntity.setCategory(categoryEntity);
        productEntity.setName(domainProduct.getName());
        productEntity.setDescription(domainProduct.getDescription());
        if (domainProduct.getBasePrice() != null) {
            productEntity.setBasePrice(domainProduct.getBasePrice().getAmount());
        }
        productEntity.setStatus(toEntityStatus(domainProduct.getStatus()));
        productEntity.setConditionType(toEntityConditionType(domainProduct.getConditionType()));
        productEntity.setBrand(domainProduct.getBrand());
        productEntity.setMainImageUrl(domainProduct.getMainImageUrl());

        mergeOptionGroups(domainProduct, productEntity);
        mergeVariants(domainProduct, productEntity);
    }

    private void mergeOptionGroups(Product domainProduct, ProductEntity productEntity) {
        for (ProductOptionGroup domainGroup : domainProduct.getOptionGroups()) {
            ProductOptionGroupEntity groupEntity = productEntity.getOptionGroups().stream()
                    .filter(e -> Objects.equals(e.getId(), domainGroup.getId().getValue()))
                    .findFirst()
                    .orElseGet(() -> {
                        ProductOptionGroupEntity newEntity = new ProductOptionGroupEntity();
                        newEntity.setId(domainGroup.getId().getValue());
                        productEntity.addOptionGroup(newEntity);
                        return newEntity;
                    });

            groupEntity.setOptionGroupId(domainGroup.getOptionGroupId().getValue());
            groupEntity.setStepOrder(domainGroup.getStepOrder());
            groupEntity.setRequired(domainGroup.isRequired());

            mergeOptionValues(domainGroup, groupEntity);
        }
    }

    private void mergeOptionValues(ProductOptionGroup domainGroup, ProductOptionGroupEntity groupEntity) {
        for (ProductOptionValue domainValue : domainGroup.getOptionValues()) {
            ProductOptionValueEntity valueEntity = groupEntity.getOptionValues().stream()
                    .filter(e -> Objects.equals(e.getId(), domainValue.getId().getValue()))
                    .findFirst()
                    .orElseGet(() -> {
                        ProductOptionValueEntity newEntity = new ProductOptionValueEntity();
                        newEntity.setId(domainValue.getId().getValue());
                        groupEntity.addOptionValue(newEntity);
                        return newEntity;
                    });

            valueEntity.setOptionValueId(domainValue.getOptionValueId().getValue());
            valueEntity.setPriceDelta(domainValue.getPriceDelta().getAmount());
            valueEntity.setDefault(domainValue.isDefault());
            valueEntity.setActive(domainValue.isActive());
        }
    }

    private void mergeVariants(Product domainProduct, ProductEntity productEntity) {
        for (ProductVariant domainVariant : domainProduct.getVariants()) {
            ProductVariantEntity variantEntity = productEntity.getVariants().stream()
                    .filter(e -> Objects.equals(e.getId(), domainVariant.getId().getValue()))
                    .findFirst()
                    .orElseGet(() -> {
                        ProductVariantEntity newEntity = new ProductVariantEntity();
                        newEntity.setId(domainVariant.getId().getValue());
                        productEntity.addVariant(newEntity);
                        return newEntity;
                    });

            variantEntity.setSku(domainVariant.getSku());
            variantEntity.setStockQuantity(domainVariant.getStockQuantity());
            variantEntity.setStatus(toEntityStatus(domainVariant.getStatus()));
            variantEntity.setCalculatedPrice(domainVariant.getCalculatedPrice().getAmount());

            mergeVariantOptionSelections(domainVariant, variantEntity);
        }
    }

    private void mergeVariantOptionSelections(ProductVariant domainVariant, ProductVariantEntity variantEntity) {
        // 1. 도메인 객체가 현재 들고 있는 옵션 ID 목록
        Set<UUID> domainSelectedIds = domainVariant.getSelectedOptionValues().stream()
                .map(ProductOptionValueId::getValue)
                .collect(Collectors.toSet());

        // 2. 도메인에 없는 기존 엔티티 매핑은 삭제
        variantEntity.getSelectedOptionValues().removeIf(
                entity -> !domainSelectedIds.contains(entity.getProductOptionValueId())
        );

        // 3. 현재 엔티티에 남아있는 옵션 ID 목록
        Set<UUID> currentEntitySelectedIds = variantEntity.getSelectedOptionValues().stream()
                .map(VariantOptionValueEntity::getProductOptionValueId)
                .collect(Collectors.toSet());

        // 4. 도메인에는 있는데 엔티티에 없는 새로운 매핑 추가
        for (ProductOptionValueId selectedId : domainVariant.getSelectedOptionValues()) {
            if (!currentEntitySelectedIds.contains(selectedId.getValue())) {
                VariantOptionValueEntity selectedEntity = VariantOptionValueEntity.builder()
                        .productOptionValueId(selectedId.getValue())
                        .build();
                variantEntity.addSelectedOptionValue(selectedEntity);
            }
        }
    }

    public ProductStatusEntity toEntityStatus(ProductStatus domainStatus) {
        return ProductStatusEntity.valueOf(domainStatus.name());
    }

    public ProductStatus toDomainStatus(ProductStatusEntity entityStatus) {
        return ProductStatus.valueOf(entityStatus.name());
    }

    public ConditionTypeEntity toEntityConditionType(ConditionType domainConditionType) {
        return ConditionTypeEntity.valueOf(domainConditionType.name());
    }

    public ConditionType toDomainConditionType(ConditionTypeEntity entityConditionType) {
        return ConditionType.valueOf(entityConditionType.name());
    }

    private CategoryId toCategoryId(ProductEntity productEntity) {
        CategoryEntity category = productEntity.getCategory();
        return (category != null) ? new CategoryId(category.getId()) : null;
    }

    private List<ProductOptionGroup> mapOptionGroupsToDomain(Set<ProductOptionGroupEntity> entities) {
        if (entities == null || entities.isEmpty()) return new ArrayList<>();
        return entities.stream().map(this::toDomainOptionGroup).toList();
    }

    private ProductOptionGroup toDomainOptionGroup(ProductOptionGroupEntity entity) {
        List<ProductOptionValue> optionValues = (entity.getOptionValues() == null)
                ? new ArrayList<>()
                : entity.getOptionValues().stream().map(this::toDomainOptionValue).toList();

        return ProductOptionGroup.reconstitute(
                new ProductOptionGroupId(entity.getId()),
                new OptionGroupId(entity.getOptionGroupId()),
                entity.getStepOrder(),
                entity.isRequired(),
                optionValues
        );
    }

    private ProductOptionValue toDomainOptionValue(ProductOptionValueEntity entity) {
        return ProductOptionValue.reconstitute(
                new ProductOptionValueId(entity.getId()),
                new OptionValueId(entity.getOptionValueId()),
                new Money(entity.getPriceDelta()),
                entity.isDefault(),
                entity.isActive()
        );
    }

    private List<ProductVariant> mapVariantsToDomain(Set<ProductVariantEntity> entities) {
        if (entities == null || entities.isEmpty()) return new ArrayList<>();
        return entities.stream().map(this::toDomainVariant).toList();
    }

    private ProductVariant toDomainVariant(ProductVariantEntity entity) {
        Set<ProductOptionValueId> selected = (entity.getSelectedOptionValues() == null)
                ? Set.of()
                : entity.getSelectedOptionValues().stream()
                .map(v -> new ProductOptionValueId(v.getProductOptionValueId()))
                .collect(Collectors.toSet());

        return ProductVariant.reconstitute(
                new ProductVariantId(entity.getId()),
                entity.getSku(),
                entity.getStockQuantity(),
                toDomainStatus(entity.getStatus()),
                new Money(entity.getCalculatedPrice()),
                selected
        );
    }
}