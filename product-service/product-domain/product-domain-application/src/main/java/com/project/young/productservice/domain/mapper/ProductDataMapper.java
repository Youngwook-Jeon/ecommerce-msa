package com.project.young.productservice.domain.mapper;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.productservice.domain.dto.CreateProductCommand;
import com.project.young.productservice.domain.dto.CreateProductResponse;
import com.project.young.productservice.domain.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductDataMapper {

    public Product createProductCommandToProduct(CreateProductCommand createProductCommand) {
        return Product.builder()
                .productName(createProductCommand.getProductName())
                .description(createProductCommand.getDescription())
                .price(new Money(createProductCommand.getPrice()))
                .build();
    }

    public CreateProductResponse productToCreateProductResponse(Product product, String message) {
        return new CreateProductResponse(product.getProductName(), message);
    }
}
