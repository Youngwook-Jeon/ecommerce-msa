package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.domain.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductDataAccessMapper {

    public ProductEntity productToProductEntity(Product product) {
        return ProductEntity.builder()
                .id(product.getId() != null ? product.getId().getValue() : null)
                .name(product.getName())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .categoryId(product.getCategoryId().map(CategoryId::getValue).orElse(null))
                .brandId(product.getBrandId().map(BrandId::getValue).orElse(null))
                .conditionType(product.getConditionType())
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public Product productEntityToProduct(ProductEntity productEntity) {
        return Product.builder()
                .productId(new ProductId(productEntity.getId()))
                .name(productEntity.getName())
                .description(productEntity.getDescription())
                .basePrice(productEntity.getBasePrice())
                .categoryId(productEntity.getCategoryId() != null ? new CategoryId(productEntity.getCategoryId()) : null)
                .brandId(productEntity.getBrandId() != null ? new BrandId(productEntity.getBrandId()) : null)
                .conditionType(productEntity.getConditionType())
                .status(productEntity.getStatus())
                .createdAt(productEntity.getCreatedAt())
                .updatedAt(productEntity.getUpdatedAt())
                .build();
    }
}