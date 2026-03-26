package com.project.young.productservice.application.mapper;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.common.domain.valueobject.ProductOptionGroupId;
import com.project.young.common.domain.valueobject.ProductOptionValueId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.application.dto.command.AddProductOptionGroupCommand;
import com.project.young.productservice.application.dto.result.AddProductOptionGroupResult;
import com.project.young.productservice.application.dto.command.AddProductOptionValueCommand;
import com.project.young.productservice.application.dto.command.AddProductVariantCommand;
import com.project.young.productservice.application.dto.result.AddProductVariantResult;
import com.project.young.productservice.application.dto.result.ChangeProductOptionValuePriceDeltaResult;
import com.project.young.productservice.application.dto.command.CreateProductCommand;
import com.project.young.productservice.application.dto.result.CreateProductResult;
import com.project.young.productservice.application.dto.result.DeactivateProductOptionValueResult;
import com.project.young.productservice.application.dto.result.DeleteProductResult;
import com.project.young.productservice.application.dto.result.DeleteProductVariantResult;
import com.project.young.productservice.application.dto.result.UpdateProductVariantResult;
import com.project.young.productservice.application.dto.result.UpdateProductResult;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.entity.ProductOptionGroup;
import com.project.young.productservice.domain.entity.ProductOptionValue;
import com.project.young.productservice.domain.entity.ProductVariant;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;

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
                .status(command.getStatus())
                .build();
    }

    public Product toDraftProduct(CreateProductCommand command, CategoryId categoryId) {
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
                .status(ProductStatus.DRAFT)
                .build();
    }

    public ProductOptionValue toProductOptionValue(AddProductOptionValueCommand command, ProductOptionValueId id) {
        Objects.requireNonNull(command, "AddProductOptionValueCommand cannot be null");
        Objects.requireNonNull(id, "ProductOptionValueId cannot be null");

        return ProductOptionValue.builder()
                .id(id)
                .optionValueId(new OptionValueId(command.getOptionValueId()))
                .priceDelta(new Money(command.getPriceDelta()))
                .isDefault(command.isDefault())
                .isActive(command.isActive())
                .build();
    }

    public ProductOptionGroup toProductOptionGroup(
            AddProductOptionGroupCommand command,
            ProductOptionGroupId id,
            List<ProductOptionValue> optionValues
    ) {
        Objects.requireNonNull(command, "AddProductOptionGroupCommand cannot be null");
        Objects.requireNonNull(id, "ProductOptionGroupId cannot be null");

        return ProductOptionGroup.builder()
                .id(id)
                .optionGroupId(new OptionGroupId(command.getOptionGroupId()))
                .stepOrder(command.getStepOrder())
                .isRequired(command.isRequired())
                .optionValues(optionValues)
                .build();
    }

    public AddProductOptionGroupResult toAddProductOptionGroupResult(Product product, ProductOptionGroup optionGroup) {
        Objects.requireNonNull(product, "Product cannot be null");
        Objects.requireNonNull(optionGroup, "ProductOptionGroup cannot be null");

        return AddProductOptionGroupResult.builder()
                .productId(product.getId().getValue())
                .productOptionGroupId(optionGroup.getId().getValue())
                .optionGroupId(optionGroup.getOptionGroupId().getValue())
                .stepOrder(optionGroup.getStepOrder())
                .required(optionGroup.isRequired())
                .optionValueCount(optionGroup.getOptionValues().size())
                .build();
    }

    public ProductVariant toProductVariant(
            AddProductVariantCommand command,
            ProductVariantId id,
            String generatedSku,
            Set<ProductOptionValueId> selectedOptionValueIds
    ) {
        Objects.requireNonNull(command, "AddProductVariantCommand cannot be null");
        Objects.requireNonNull(id, "ProductVariantId cannot be null");

        return ProductVariant.builder()
                .id(id)
                .sku(generatedSku)
                .stockQuantity(command.getStockQuantity())
                .selectedOptionValues(selectedOptionValueIds)
                .build();
    }

    public AddProductVariantResult toAddProductVariantResult(Product product, ProductVariant variant) {
        Objects.requireNonNull(product, "Product cannot be null");
        Objects.requireNonNull(variant, "ProductVariant cannot be null");

        return AddProductVariantResult.builder()
                .productId(product.getId().getValue())
                .productVariantId(variant.getId().getValue())
                .sku(variant.getSku())
                .stockQuantity(variant.getStockQuantity())
                .status(variant.getStatus())
                .calculatedPrice(variant.getCalculatedPrice().getAmount())
                .build();
    }

    public ChangeProductOptionValuePriceDeltaResult toChangeProductOptionValuePriceDeltaResult(
            Product product,
            ProductOptionValueId optionValueId,
            Money priceDelta
    ) {
        Objects.requireNonNull(product, "Product cannot be null");
        Objects.requireNonNull(optionValueId, "ProductOptionValueId cannot be null");
        Objects.requireNonNull(priceDelta, "PriceDelta cannot be null");

        return ChangeProductOptionValuePriceDeltaResult.builder()
                .productId(product.getId().getValue())
                .productOptionValueId(optionValueId.getValue())
                .priceDelta(priceDelta.getAmount())
                .build();
    }

    public UpdateProductVariantResult toUpdateProductVariantResult(Product product, ProductVariant variant) {
        Objects.requireNonNull(product, "Product cannot be null");
        Objects.requireNonNull(variant, "ProductVariant cannot be null");

        return UpdateProductVariantResult.builder()
                .productId(product.getId().getValue())
                .productVariantId(variant.getId().getValue())
                .sku(variant.getSku())
                .stockQuantity(variant.getStockQuantity())
                .status(variant.getStatus())
                .calculatedPrice(variant.getCalculatedPrice().getAmount())
                .build();
    }

    public DeleteProductVariantResult toDeleteProductVariantResult(Product product, ProductVariant variant) {
        Objects.requireNonNull(product, "Product cannot be null");
        Objects.requireNonNull(variant, "ProductVariant cannot be null");

        return DeleteProductVariantResult.builder()
                .productId(product.getId().getValue())
                .productVariantId(variant.getId().getValue())
                .sku(variant.getSku())
                .status(variant.getStatus())
                .build();
    }

    public DeactivateProductOptionValueResult toDeactivateProductOptionValueResult(Product product, ProductOptionValue optionValue) {
        Objects.requireNonNull(product, "Product cannot be null");
        Objects.requireNonNull(optionValue, "ProductOptionValue cannot be null");

        return DeactivateProductOptionValueResult.builder()
                .productId(product.getId().getValue())
                .productOptionValueId(optionValue.getId().getValue())
                .active(optionValue.isActive())
                .priceDelta(optionValue.getPriceDelta().getAmount())
                .build();
    }
}
