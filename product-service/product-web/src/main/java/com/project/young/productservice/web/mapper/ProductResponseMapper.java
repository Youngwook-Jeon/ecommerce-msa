package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.dto.result.AddProductOptionGroupResult;
import com.project.young.productservice.application.dto.result.AddProductOptionValueToGroupResult;
import com.project.young.productservice.application.dto.result.AddProductVariantResult;
import com.project.young.productservice.application.dto.result.CreateProductResult;
import com.project.young.productservice.application.dto.result.DeleteProductResult;
import com.project.young.productservice.application.dto.result.UpdateProductResult;
import com.project.young.productservice.web.converter.ConditionTypeWebConverter;
import com.project.young.productservice.web.converter.ProductStatusWebConverter;
import com.project.young.productservice.web.dto.AddProductOptionGroupResponse;
import com.project.young.productservice.web.dto.AddProductOptionValueToGroupResponse;
import com.project.young.productservice.web.dto.AddProductVariantResponse;
import com.project.young.productservice.web.dto.CreateProductResponse;
import com.project.young.productservice.web.dto.DeleteProductResponse;
import com.project.young.productservice.web.dto.UpdateProductResponse;
import com.project.young.productservice.web.message.ProductResponseMessageFactory;
import org.springframework.stereotype.Component;

@Component
public class ProductResponseMapper {

    private final ProductResponseMessageFactory messageFactory;
    private final ProductStatusWebConverter productStatusWebConverter;
    private final ConditionTypeWebConverter conditionTypeWebConverter;

    public ProductResponseMapper(ProductResponseMessageFactory messageFactory,
                                 ProductStatusWebConverter productStatusWebConverter,
                                 ConditionTypeWebConverter conditionTypeWebConverter) {
        this.messageFactory = messageFactory;
        this.productStatusWebConverter = productStatusWebConverter;
        this.conditionTypeWebConverter = conditionTypeWebConverter;
    }

    public CreateProductResponse toCreateProductResponse(CreateProductResult result) {
        return CreateProductResponse.builder()
                .id(result.id())
                .name(result.name())
                .message(messageFactory.productCreated())
                .build();
    }

    public UpdateProductResponse toUpdateProductResponse(UpdateProductResult result) {
        return UpdateProductResponse.builder()
                .id(result.id())
                .categoryId(result.categoryId())
                .name(result.name())
                .description(result.description())
                .basePrice(result.basePrice())
                .brand(result.brand())
                .mainImageUrl(result.mainImageUrl())
                .conditionType(conditionTypeWebConverter.toStringValue(result.conditionType()))
                .status(productStatusWebConverter.toStringValue(result.status()))
                .message(messageFactory.productUpdated())
                .build();
    }

    public DeleteProductResponse toDeleteProductResponse(DeleteProductResult result) {
        return DeleteProductResponse.builder()
                .id(result.id())
                .name(result.name())
                .message(messageFactory.productDeleted())
                .build();
    }

    public AddProductOptionGroupResponse toAddProductOptionGroupResponse(AddProductOptionGroupResult result) {
        return AddProductOptionGroupResponse.builder()
                .productId(result.productId())
                .productOptionGroupId(result.productOptionGroupId())
                .optionGroupId(result.optionGroupId())
                .stepOrder(result.stepOrder())
                .required(result.required())
                .optionValueCount(result.optionValueCount())
                .message(messageFactory.productOptionGroupAdded())
                .build();
    }

    public AddProductOptionValueToGroupResponse toAddProductOptionValueToGroupResponse(
            AddProductOptionValueToGroupResult result
    ) {
        return AddProductOptionValueToGroupResponse.builder()
                .productId(result.productId())
                .productOptionGroupId(result.productOptionGroupId())
                .productOptionValueId(result.productOptionValueId())
                .optionValueId(result.optionValueId())
                .priceDelta(result.priceDelta())
                .message(messageFactory.productOptionValueAdded())
                .build();
    }

    public AddProductVariantResponse toAddProductVariantResponse(AddProductVariantResult result) {
        return AddProductVariantResponse.builder()
                .productId(result.productId())
                .productVariantId(result.productVariantId())
                .sku(result.sku())
                .stockQuantity(result.stockQuantity())
                .status(productStatusWebConverter.toStringValue(result.status()))
                .calculatedPrice(result.calculatedPrice())
                .message(messageFactory.productVariantAdded())
                .build();
    }
}
