package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.dto.CreateProductResult;
import com.project.young.productservice.application.dto.DeleteProductResult;
import com.project.young.productservice.application.dto.UpdateProductResult;
import com.project.young.productservice.web.converter.ConditionTypeWebConverter;
import com.project.young.productservice.web.converter.ProductStatusWebConverter;
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
}
