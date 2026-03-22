package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

@Component
public class ProductDataAccessMapper {

    public Product productEntityToProduct(ProductEntity productEntity) {
        Objects.requireNonNull(productEntity, "productEntity must not be null.");

        ProductId productId = new ProductId(productEntity.getId());
        CategoryId categoryId = toCategoryId(productEntity);

        Money basePrice = new Money(productEntity.getBasePrice());

        ProductStatus status = toDomainStatus(productEntity.getStatus());
        ConditionType conditionType = toDomainConditionType(productEntity.getConditionType());

        return Product.reconstitute(
                productId,
                categoryId,
                productEntity.getName(),
                productEntity.getDescription(),
                basePrice,
                status,
                conditionType,
                productEntity.getBrand(),
                productEntity.getMainImageUrl(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    public ProductEntity productToProductEntity(Product product, CategoryEntity categoryEntity) {
        Objects.requireNonNull(product, "product must not be null.");

        UUID id = product.getId() != null ? product.getId().getValue() : null;

        return ProductEntity.builder()
                .id(id)
                .category(categoryEntity)
                .name(product.getName())
                .description(product.getDescription())
                .basePrice(product.getBasePrice().getAmount())
                .status(toEntityStatus(product.getStatus()))
                .conditionType(toEntityConditionType(product.getConditionType()))
                .brand(product.getBrand())
                .mainImageUrl(product.getMainImageUrl())
                .build();
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
    }

    public ProductStatusEntity toEntityStatus(ProductStatus domainStatus) {
        Objects.requireNonNull(domainStatus, "domainStatus must not be null.");
        return ProductStatusEntity.valueOf(domainStatus.name());
    }

    public ProductStatus toDomainStatus(ProductStatusEntity entityStatus) {
        Objects.requireNonNull(entityStatus, "entityStatus must not be null.");
        return ProductStatus.valueOf(entityStatus.name());
    }

    public ConditionTypeEntity toEntityConditionType(ConditionType domainConditionType) {
        Objects.requireNonNull(domainConditionType, "domainConditionType must not be null.");
        return ConditionTypeEntity.valueOf(domainConditionType.name());
    }

    public ConditionType toDomainConditionType(ConditionTypeEntity entityConditionType) {
        Objects.requireNonNull(entityConditionType, "entityConditionType must not be null.");
        return ConditionType.valueOf(entityConditionType.name());
    }

    private CategoryId toCategoryId(ProductEntity productEntity) {
        CategoryEntity category = productEntity.getCategory();
        return (category != null) ? new CategoryId(category.getId()) : null;
    }
}

