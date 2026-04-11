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

/**
 * Maps a fully loaded {@link ProductEntity} aggregate (e.g. from {@code findAggregateById} with entity graph)
 * to the domain model. Do not use on partially initialized / lazy associations — that risks N+1 selects.
 */
@Component
public class ProductAggregateMapper {

    public Product toProduct(ProductEntity productEntity) {
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

    private CategoryId toCategoryId(ProductEntity productEntity) {
        CategoryEntity category = productEntity.getCategory();
        return (category != null) ? new CategoryId(category.getId()) : null;
    }

    private List<ProductOptionGroup> mapOptionGroupsToDomain(Set<ProductOptionGroupEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }
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
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }
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

    private ProductStatus toDomainStatus(ProductStatusEntity entityStatus) {
        return ProductStatus.valueOf(entityStatus.name());
    }

    private ConditionType toDomainConditionType(ConditionTypeEntity entityConditionType) {
        return ConditionType.valueOf(entityConditionType.name());
    }
}
