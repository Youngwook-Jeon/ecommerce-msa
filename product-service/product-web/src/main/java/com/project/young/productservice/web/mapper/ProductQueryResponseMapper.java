package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionValueView;
import com.project.young.productservice.application.port.output.view.ReadProductVariantView;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.web.converter.ConditionTypeWebConverter;
import com.project.young.productservice.web.converter.OptionStatusWebConverter;
import com.project.young.productservice.web.converter.ProductStatusWebConverter;
import com.project.young.productservice.web.dto.ReadProductDetailResponse;
import com.project.young.productservice.web.dto.ReadProductOptionGroupResponse;
import com.project.young.productservice.web.dto.ReadProductOptionValueResponse;
import com.project.young.productservice.web.dto.ReadProductListResponse;
import com.project.young.productservice.web.dto.ReadProductVariantResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class ProductQueryResponseMapper {

    private final ProductStatusWebConverter productStatusWebConverter;
    private final ConditionTypeWebConverter conditionTypeWebConverter;
    private final OptionStatusWebConverter optionStatusWebConverter;

    public ProductQueryResponseMapper(ProductStatusWebConverter productStatusWebConverter,
                                      ConditionTypeWebConverter conditionTypeWebConverter,
                                      OptionStatusWebConverter optionStatusWebConverter) {
        this.productStatusWebConverter = productStatusWebConverter;
        this.conditionTypeWebConverter = conditionTypeWebConverter;
        this.optionStatusWebConverter = optionStatusWebConverter;
    }

    public ReadProductDetailResponse toReadProductDetailResponse(ReadProductDetailView readProductView) {
        Objects.requireNonNull(readProductView, "ReadProductDetailView is null");
        return ReadProductDetailResponse.builder()
                .id(readProductView.id())
                .categoryId(readProductView.categoryId())
                .name(readProductView.name())
                .description(readProductView.description())
                .basePrice(readProductView.basePrice())
                .brand(readProductView.brand())
                .mainImageUrl(readProductView.mainImageUrl())
                .conditionType(conditionTypeWebConverter.toStringValue(readProductView.conditionType()))
                .status(productStatusWebConverter.toStringValue(readProductView.status()))
                .optionGroups(readProductView.optionGroups().stream().map(this::toReadProductOptionGroupResponse).toList())
                .variants(readProductView.variants().stream().map(this::toReadProductVariantResponse).toList())
                .build();
    }

    public ReadProductListResponse toReadProductListResponse(List<ReadProductView> readProductViews) {
        return ReadProductListResponse.builder()
                .products(readProductViews.stream()
                        .map(this::toReadProductDetailResponseForList)
                        .toList())
                .build();
    }

    private ReadProductDetailResponse toReadProductDetailResponseForList(ReadProductView readProductView) {
        Objects.requireNonNull(readProductView, "ReadProductView is null");
        return ReadProductDetailResponse.builder()
                .id(readProductView.id())
                .categoryId(readProductView.categoryId())
                .name(readProductView.name())
                .description(readProductView.description())
                .basePrice(readProductView.basePrice())
                .brand(readProductView.brand())
                .mainImageUrl(readProductView.mainImageUrl())
                .conditionType(conditionTypeWebConverter.toStringValue(readProductView.conditionType()))
                .status(productStatusWebConverter.toStringValue(readProductView.status()))
                .build();
    }

    private ReadProductOptionGroupResponse toReadProductOptionGroupResponse(ReadProductOptionGroupView view) {
        return ReadProductOptionGroupResponse.builder()
                .productOptionGroupId(view.productOptionGroupId())
                .optionGroupId(view.optionGroupId())
                .stepOrder(view.stepOrder())
                .required(view.required())
                .status(optionStatusWebConverter.toStringValue(view.status()))
                .optionValues(view.optionValues().stream().map(this::toReadProductOptionValueResponse).toList())
                .build();
    }

    private ReadProductOptionValueResponse toReadProductOptionValueResponse(ReadProductOptionValueView view) {
        return ReadProductOptionValueResponse.builder()
                .productOptionValueId(view.productOptionValueId())
                .optionValueId(view.optionValueId())
                .priceDelta(view.priceDelta())
                .isDefault(view.isDefault())
                .status(optionStatusWebConverter.toStringValue(view.status()))
                .build();
    }

    private ReadProductVariantResponse toReadProductVariantResponse(ReadProductVariantView view) {
        return ReadProductVariantResponse.builder()
                .productVariantId(view.productVariantId())
                .sku(view.sku())
                .stockQuantity(view.stockQuantity())
                .status(productStatusWebConverter.toStringValue(view.status()))
                .calculatedPrice(view.calculatedPrice())
                .selectedProductOptionValueIds(view.selectedProductOptionValueIds())
                .build();
    }
}
