package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.domain.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductDataAccessMapper {

    public Product productEntityToProduct(ProductEntity productEntity) {
        return Product.builder()
                .productId(new ProductId(productEntity.getProductId()))
                .productName(productEntity.getProductName())
                .description(productEntity.getDescription())
                .price(new Money(productEntity.getPrice()))
                .build();
    }

    public ProductEntity productToProductEntity(Product product) {
        return ProductEntity.builder()
                .productId(product.getId().getValue())
                .productName(product.getProductName())
                .description(product.getDescription())
                .price(product.getPrice().getAmount())
                .build();
    }
}
