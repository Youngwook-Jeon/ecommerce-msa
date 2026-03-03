package com.project.young.productservice.application.mapper;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.productservice.application.dto.CreateProductCommand;
import com.project.young.productservice.application.dto.CreateProductResult;
import com.project.young.productservice.application.dto.DeleteProductResult;
import com.project.young.productservice.application.dto.UpdateProductResult;
import com.project.young.productservice.domain.entity.Product;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ProductDataMapper {

    public CreateProductResult toCreateProductResult(Product product) {
        Objects.requireNonNull(product, "Product cannot be null");
        Objects.requireNonNull(product.getId(), "Product ID cannot be null");

        return CreateProductResult.builder()
                .id(product.getId().getValue())
                .name(product.getName())
                .build();
    }

    public UpdateProductResult toUpdateProductResult(Product product) {
        Objects.requireNonNull(product, "Product cannot be null");
        Objects.requireNonNull(product.getId(), "Product ID cannot be null");

        Long categoryId = product.getCategoryId()
                .map(CategoryId::getValue)
                .orElse(null);

        return UpdateProductResult.builder()
                .id(product.getId().getValue())
                .name(product.getName())
                .categoryId(categoryId)
                .description(product.getDescription())
                .brand(product.getBrand())
                .mainImageUrl(product.getMainImageUrl())
                .basePrice(product.getBasePrice() != null ? product.getBasePrice().getAmount() : null)
                .status(product.getStatus())
                .conditionType(product.getConditionType())
                .build();
    }

    public DeleteProductResult toDeleteProductResult(Product product) {
        Objects.requireNonNull(product, "Product cannot be null");
        Objects.requireNonNull(product.getId(), "Product ID cannot be null");

        return DeleteProductResult.builder()
                .id(product.getId().getValue())
                .name(product.getName())
                .build();
    }

    public Product toProduct(CreateProductCommand command, CategoryId categoryId) {
        Objects.requireNonNull(command, "CreateProductCommand cannot be null");

        Money basePrice = new Money(command.getBasePrice());

        return Product.builder()
                .categoryId(categoryId)
                .name(command.getName())
                .description(command.getDescription())
                .basePrice(basePrice)
                .brand(command.getBrand())
                .mainImageUrl(command.getMainImageUrl())
                .conditionType(command.getConditionType())
                .status(command.getProductStatus())
                .build();
    }
}
