package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.dto.result.AddProductOptionGroupResult;
import com.project.young.productservice.application.dto.result.AddProductOptionValueToGroupResult;
import com.project.young.productservice.application.dto.result.AddProductVariantResult;
import com.project.young.productservice.application.dto.result.ChangeProductOptionGroupStepOrderResult;
import com.project.young.productservice.application.dto.result.CreateProductResult;
import com.project.young.productservice.application.dto.result.DeleteProductOptionGroupResult;
import com.project.young.productservice.application.dto.result.DeleteProductOptionValueResult;
import com.project.young.productservice.application.dto.result.DeleteProductResult;
import com.project.young.productservice.application.dto.result.DeleteProductVariantResult;
import com.project.young.productservice.application.dto.result.UpdateProductResult;
import com.project.young.productservice.application.dto.result.UpdateProductVariantResult;
import com.project.young.productservice.application.dto.result.ReorderProductOptionGroupsResult;
import com.project.young.productservice.application.dto.result.UpdateProductOptionGroupVisualResult;
import com.project.young.productservice.web.converter.ConditionTypeWebConverter;
import com.project.young.productservice.web.converter.OptionStatusWebConverter;
import com.project.young.productservice.web.converter.ProductStatusWebConverter;
import com.project.young.productservice.web.dto.AddProductOptionGroupResponse;
import com.project.young.productservice.web.dto.AddProductOptionValueToGroupResponse;
import com.project.young.productservice.web.dto.AddProductVariantResponse;
import com.project.young.productservice.web.dto.ChangeProductOptionGroupStepOrderResponse;
import com.project.young.productservice.web.dto.CreateProductResponse;
import com.project.young.productservice.web.dto.DeleteProductOptionGroupResponse;
import com.project.young.productservice.web.dto.DeleteProductOptionValueResponse;
import com.project.young.productservice.web.dto.DeleteProductResponse;
import com.project.young.productservice.web.dto.DeleteProductVariantResponse;
import com.project.young.productservice.web.dto.UpdateProductResponse;
import com.project.young.productservice.web.dto.ReorderProductOptionGroupsResponse;
import com.project.young.productservice.web.dto.UpdateProductOptionGroupVisualResponse;
import com.project.young.productservice.web.dto.UpdateProductVariantResponse;
import com.project.young.productservice.web.message.ProductResponseMessageFactory;
import org.springframework.stereotype.Component;

@Component
public class ProductResponseMapper {

    private final ProductResponseMessageFactory messageFactory;
    private final ProductStatusWebConverter productStatusWebConverter;
    private final ConditionTypeWebConverter conditionTypeWebConverter;
    private final OptionStatusWebConverter optionStatusWebConverter;

    public ProductResponseMapper(ProductResponseMessageFactory messageFactory,
                                 ProductStatusWebConverter productStatusWebConverter,
                                 ConditionTypeWebConverter conditionTypeWebConverter,
                                 OptionStatusWebConverter optionStatusWebConverter) {
        this.messageFactory = messageFactory;
        this.productStatusWebConverter = productStatusWebConverter;
        this.conditionTypeWebConverter = conditionTypeWebConverter;
        this.optionStatusWebConverter = optionStatusWebConverter;
    }

    public CreateProductResponse toCreateProductResponse(CreateProductResult result) {
        return CreateProductResponse.builder()
                .id(result.id())
                .name(result.name())
                .message(messageFactory.productCreated())
                .build();
    }

    public UpdateProductResponse toUpdateProductResponse(UpdateProductResult result) {
        return toUpdateProductResponse(result, messageFactory.productUpdated());
    }

    public UpdateProductResponse toUpdateProductStatusResponse(UpdateProductResult result) {
        return toUpdateProductResponse(result, messageFactory.productStatusUpdated());
    }

    private UpdateProductResponse toUpdateProductResponse(UpdateProductResult result, String message) {
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
                .message(message)
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

    public UpdateProductVariantResponse toUpdateProductVariantResponse(UpdateProductVariantResult result) {
        return UpdateProductVariantResponse.builder()
                .productId(result.productId())
                .productVariantId(result.productVariantId())
                .sku(result.sku())
                .stockQuantity(result.stockQuantity())
                .status(productStatusWebConverter.toStringValue(result.status()))
                .calculatedPrice(result.calculatedPrice())
                .message(messageFactory.productVariantUpdated())
                .build();
    }

    public DeleteProductVariantResponse toDeleteProductVariantResponse(DeleteProductVariantResult result) {
        return DeleteProductVariantResponse.builder()
                .productId(result.productId())
                .productVariantId(result.productVariantId())
                .sku(result.sku())
                .status(productStatusWebConverter.toStringValue(result.status()))
                .message(messageFactory.productVariantDeleted())
                .build();
    }

    public DeleteProductOptionGroupResponse toDeleteProductOptionGroupResponse(
            DeleteProductOptionGroupResult result
    ) {
        return DeleteProductOptionGroupResponse.builder()
                .productId(result.productId())
                .productOptionGroupId(result.productOptionGroupId())
                .status(optionStatusWebConverter.toStringValue(result.status()))
                .stepOrder(result.stepOrder())
                .message(messageFactory.productOptionGroupDeleted())
                .build();
    }

    public DeleteProductOptionValueResponse toDeleteProductOptionValueResponse(
            DeleteProductOptionValueResult result
    ) {
        return DeleteProductOptionValueResponse.builder()
                .productId(result.productId())
                .productOptionValueId(result.productOptionValueId())
                .status(optionStatusWebConverter.toStringValue(result.status()))
                .priceDelta(result.priceDelta())
                .message(messageFactory.productOptionValueDeleted())
                .build();
    }

    public ChangeProductOptionGroupStepOrderResponse toChangeProductOptionGroupStepOrderResponse(
            ChangeProductOptionGroupStepOrderResult result
    ) {
        return ChangeProductOptionGroupStepOrderResponse.builder()
                .productId(result.productId())
                .productOptionGroupId(result.productOptionGroupId())
                .stepOrder(result.stepOrder())
                .message(messageFactory.productOptionGroupStepOrderUpdated())
                .build();
    }

    public ReorderProductOptionGroupsResponse toReorderProductOptionGroupsResponse(
            ReorderProductOptionGroupsResult result
    ) {
        return ReorderProductOptionGroupsResponse.builder()
                .productId(result.productId())
                .updatedCount(result.updatedCount())
                .message(messageFactory.productOptionGroupsReordered())
                .build();
    }

    public UpdateProductOptionGroupVisualResponse toUpdateProductOptionGroupVisualResponse(
            UpdateProductOptionGroupVisualResult result
    ) {
        return UpdateProductOptionGroupVisualResponse.builder()
                .productId(result.productId())
                .productOptionGroupId(result.productOptionGroupId())
                .drivesVariantImages(result.drivesVariantImages())
                .message(messageFactory.productOptionGroupVisualUpdated())
                .build();
    }
}
