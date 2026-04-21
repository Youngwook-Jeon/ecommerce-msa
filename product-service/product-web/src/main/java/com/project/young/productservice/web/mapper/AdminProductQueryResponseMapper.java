package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.dto.result.AdminProductDetailResult;
import com.project.young.productservice.application.port.output.view.ReadProductOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionValueView;
import com.project.young.productservice.application.port.output.view.ReadProductVariantView;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.web.converter.ConditionTypeWebConverter;
import com.project.young.productservice.web.converter.OptionStatusWebConverter;
import com.project.young.productservice.web.converter.ProductStatusWebConverter;
import com.project.young.productservice.web.dto.AdminProductDetailResponse;
import com.project.young.productservice.web.dto.AdminProductListItemResponse;
import com.project.young.productservice.web.dto.AdminProductPageResponse;
import com.project.young.productservice.web.dto.ReadProductOptionGroupResponse;
import com.project.young.productservice.web.dto.ReadProductOptionValueResponse;
import com.project.young.productservice.web.dto.ReadProductVariantResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminProductQueryResponseMapper {

    private final ProductStatusWebConverter productStatusWebConverter;
    private final ConditionTypeWebConverter conditionTypeWebConverter;
    private final OptionStatusWebConverter optionStatusWebConverter;

    public AdminProductQueryResponseMapper(ProductStatusWebConverter productStatusWebConverter,
                                           ConditionTypeWebConverter conditionTypeWebConverter,
                                           OptionStatusWebConverter optionStatusWebConverter) {
        this.productStatusWebConverter = productStatusWebConverter;
        this.conditionTypeWebConverter = conditionTypeWebConverter;
        this.optionStatusWebConverter = optionStatusWebConverter;
    }

    public AdminProductDetailResponse toAdminProductDetailResponse(AdminProductDetailResult result) {
        return AdminProductDetailResponse.builder()
                .id(result.id())
                .categoryId(result.categoryId())
                .name(result.name())
                .description(result.description())
                .brand(result.brand())
                .mainImageUrl(result.mainImageUrl())
                .basePrice(result.basePrice())
                .status(productStatusWebConverter.toStringValue(result.status()))
                .conditionType(conditionTypeWebConverter.toStringValue(result.conditionType()))
                .createdAt(result.createdAt())
                .updatedAt(result.updatedAt())
                .optionGroups(result.optionGroups().stream().map(this::toReadProductOptionGroupResponse).toList())
                .variants(result.variants().stream().map(this::toReadProductVariantResponse).toList())
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

    public AdminProductPageResponse toAdminProductPageResponse(
            List<ReadProductView> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        List<AdminProductListItemResponse> items = content.stream()
                .map(this::toAdminProductListItemResponse)
                .toList();

        return AdminProductPageResponse.builder()
                .content(items)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }

    private AdminProductListItemResponse toAdminProductListItemResponse(ReadProductView view) {
        return AdminProductListItemResponse.builder()
                .id(view.id())
                .categoryId(view.categoryId())
                .name(view.name())
                .brand(view.brand())
                .mainImageUrl(view.mainImageUrl())
                .basePrice(view.basePrice())
                .status(productStatusWebConverter.toStringValue(view.status()))
                .conditionType(conditionTypeWebConverter.toStringValue(view.conditionType()))
                .build();
    }
}
