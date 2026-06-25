package com.project.young.productservice.web.publicapi.mapper;

import com.project.young.productservice.application.port.output.view.ReadCartCatalogLineView;
import com.project.young.productservice.application.port.output.view.ReadCartCatalogOptionLineView;
import com.project.young.productservice.web.publicapi.dto.PublicCartCatalogLineResponse;
import com.project.young.productservice.web.publicapi.dto.PublicCartCatalogLinesResponse;
import com.project.young.productservice.web.publicapi.dto.PublicCartCatalogOptionLineResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PublicCartCatalogQueryResponseMapper {

    public PublicCartCatalogLinesResponse toResponse(List<ReadCartCatalogLineView> lines) {
        List<PublicCartCatalogLineResponse> mapped = lines == null
                ? List.of()
                : lines.stream().map(this::toLine).toList();
        return PublicCartCatalogLinesResponse.builder().lines(mapped).build();
    }

    private PublicCartCatalogLineResponse toLine(ReadCartCatalogLineView view) {
        return PublicCartCatalogLineResponse.builder()
                .productId(view.productId())
                .productVariantId(view.productVariantId())
                .productName(view.productName())
                .brand(view.brand())
                .sku(view.sku())
                .imageUrl(view.imageUrl())
                .unitPrice(view.unitPrice())
                .purchasable(view.purchasable())
                .stockQuantity(view.stockQuantity())
                .variantOptions(view.variantOptions().stream().map(this::toOptionLine).toList())
                .build();
    }

    private PublicCartCatalogOptionLineResponse toOptionLine(ReadCartCatalogOptionLineView view) {
        return PublicCartCatalogOptionLineResponse.builder()
                .stepOrder(view.stepOrder())
                .productOptionGroupId(view.productOptionGroupId())
                .optionGroupName(view.optionGroupName())
                .productOptionValueId(view.productOptionValueId())
                .optionValueName(view.optionValueName())
                .build();
    }
}
