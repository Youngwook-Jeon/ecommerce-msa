package com.project.young.productservice.web.publicapi.mapper;

import com.project.young.productservice.application.dto.result.PublicProductListPageResult;
import com.project.young.productservice.application.policy.StorefrontProductVisibilityPolicy;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.application.port.output.view.ReadProductImageView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionValueView;
import com.project.young.productservice.application.port.output.view.ReadProductVariantView;
import com.project.young.productservice.application.port.output.view.ReadPublicProductSummaryView;
import com.project.young.productservice.web.converter.ConditionTypeWebConverter;
import com.project.young.productservice.web.converter.ProductStatusWebConverter;
import com.project.young.productservice.web.publicapi.dto.PublicProductDetailResponse;
import com.project.young.productservice.web.publicapi.dto.PublicProductImageResponse;
import com.project.young.productservice.web.publicapi.dto.PublicProductOptionGroupResponse;
import com.project.young.productservice.web.publicapi.dto.PublicProductOptionValueResponse;
import com.project.young.productservice.web.publicapi.dto.PublicProductPageResponse;
import com.project.young.productservice.web.publicapi.dto.PublicProductSummaryResponse;
import com.project.young.productservice.web.publicapi.dto.PublicProductVariantResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PublicProductQueryResponseMapper {

    private final ProductStatusWebConverter productStatusWebConverter;
    private final ConditionTypeWebConverter conditionTypeWebConverter;

    public PublicProductQueryResponseMapper(ProductStatusWebConverter productStatusWebConverter,
                                            ConditionTypeWebConverter conditionTypeWebConverter) {
        this.productStatusWebConverter = productStatusWebConverter;
        this.conditionTypeWebConverter = conditionTypeWebConverter;
    }

    public PublicProductPageResponse toPublicProductPageResponse(PublicProductListPageResult result) {
        List<PublicProductSummaryResponse> content = result.content().stream()
                .map(this::toSummary)
                .toList();

        return PublicProductPageResponse.builder()
                .content(content)
                .page(result.page())
                .size(result.size())
                .totalElements(result.totalElements())
                .totalPages(result.totalPages())
                .build();
    }

    private PublicProductSummaryResponse toSummary(ReadPublicProductSummaryView view) {
        return PublicProductSummaryResponse.builder()
                .id(view.id())
                .name(view.name())
                .brand(view.brand())
                .mainImageUrl(view.mainImageUrl())
                .basePrice(view.basePrice())
                .build();
    }

    public PublicProductDetailResponse toPublicProductDetailResponse(ReadProductDetailView view) {
        return PublicProductDetailResponse.builder()
                .id(view.id())
                .categoryId(view.categoryId())
                .name(view.name())
                .description(view.description())
                .brand(view.brand())
                .mainImageUrl(view.mainImageUrl())
                .basePrice(view.basePrice())
                .status(productStatusWebConverter.toStringValue(view.status()))
                .conditionType(conditionTypeWebConverter.toStringValue(view.conditionType()))
                .purchasable(StorefrontProductVisibilityPolicy.isPurchasable(view.status()))
                .listedInCatalog(StorefrontProductVisibilityPolicy.isListedInCatalog(view.status()))
                .images(view.images().stream().map(this::toImage).toList())
                .optionGroups(view.optionGroups().stream().map(this::toOptionGroup).toList())
                .variants(view.variants().stream().map(this::toVariant).toList())
                .build();
    }

    private PublicProductOptionGroupResponse toOptionGroup(ReadProductOptionGroupView view) {
        return PublicProductOptionGroupResponse.builder()
                .productOptionGroupId(view.productOptionGroupId())
                .optionGroupId(view.optionGroupId())
                .groupKey(view.groupKey())
                .displayName(view.displayName())
                .stepOrder(view.stepOrder())
                .required(view.required())
                .drivesVariantImages(view.drivesVariantImages())
                .optionValues(view.optionValues().stream().map(this::toOptionValue).toList())
                .build();
    }

    private PublicProductOptionValueResponse toOptionValue(ReadProductOptionValueView view) {
        return PublicProductOptionValueResponse.builder()
                .productOptionValueId(view.productOptionValueId())
                .optionValueId(view.optionValueId())
                .displayName(view.displayName())
                .priceDelta(view.priceDelta())
                .isDefault(view.isDefault())
                .images(view.images().stream().map(this::toImage).toList())
                .build();
    }

    private PublicProductVariantResponse toVariant(ReadProductVariantView view) {
        return PublicProductVariantResponse.builder()
                .productVariantId(view.productVariantId())
                .sku(view.sku())
                .stockQuantity(view.stockQuantity())
                .calculatedPrice(view.calculatedPrice())
                .mainImageUrl(view.mainImageUrl())
                .selectedProductOptionValueIds(view.selectedProductOptionValueIds())
                .build();
    }

    private PublicProductImageResponse toImage(ReadProductImageView view) {
        return PublicProductImageResponse.builder()
                .id(view.id())
                .url(view.publicUrl())
                .role(view.role())
                .sortOrder(view.sortOrder())
                .build();
    }
}
