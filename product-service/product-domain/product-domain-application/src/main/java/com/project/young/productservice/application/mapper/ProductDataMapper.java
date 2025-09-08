package com.project.young.productservice.application.mapper;

import com.project.young.common.domain.valueobject.BrandId;
import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.application.dto.product.*;
import com.project.young.productservice.domain.entity.Product;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProductDataMapper {

    public Product toProduct(CreateProductCommand command) {
        CategoryId categoryId = new CategoryId(command.getCategoryId());
        BrandId brandId = null;

        if (command.getBrandId() != null && !command.getBrandId().trim().isEmpty()) {
            brandId = new BrandId(UUID.fromString(command.getBrandId()));
        }

        return Product.builder()
                .name(command.getName())
                .description(command.getDescription())
                .basePrice(command.getBasePrice())
                .categoryId(categoryId)
                .brandId(brandId)
                .conditionType(command.getConditionType())
                .status(command.getStatus())
                .build();
    }

    public CreateProductResponse toCreateProductResponse(Product product, String message) {
        return CreateProductResponse.builder()
                .productId(product.getId().getValue().toString())
                .name(product.getName())
                .message(message)
                .build();
    }

    public UpdateProductResponse toUpdateProductResponse(Product product, String message) {
        return UpdateProductResponse.builder()
                .productId(product.getId().getValue().toString())
                .name(product.getName())
                .message(message)
                .build();
    }

    public DeleteProductResponse toDeleteProductResponse(Product product, String message) {
        return DeleteProductResponse.builder()
                .productId(product.getId().getValue().toString())
                .name(product.getName())
                .message(message)
                .build();
    }
}